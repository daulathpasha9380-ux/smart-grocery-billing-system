package com.billingsystem.billing_system;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    public static final int TOKEN_EXPIRY_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public ResetRequestResult initiatePasswordReset(String email, String baseUrl) {
        if (email == null || email.isBlank()) {
            return new ResetRequestResult(false, "Please provide a valid email address.", null, null, null);
        }

        String normalizedEmail = email.trim().toLowerCase();
        List<UserRecord> users = jdbc.query("""
                SELECT username, display_name, email, enabled
                FROM app_users
                WHERE LOWER(email) = ?
                """, (rs, rowNum) -> new UserRecord(
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getBoolean("enabled")
        ), normalizedEmail);

        if (users.isEmpty()) {
            LOGGER.warn("Password reset requested for unregistered email: {}", normalizedEmail);
            return new ResetRequestResult(false, "No registered account was found with that email address.", null, null, null);
        }

        UserRecord user = users.get(0);
        if (!user.enabled()) {
            LOGGER.warn("Password reset requested for disabled account: {}", user.username());
            return new ResetRequestResult(false, "This account is currently disabled. Please contact store management.", null, null, null);
        }

        // Invalidate prior unused tokens for this user
        jdbc.update("UPDATE password_reset_tokens SET used = TRUE WHERE username = ? AND used = FALSE", user.username());

        // Generate cryptographically random token
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Instant expiryTime = Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        jdbc.update("""
                INSERT INTO password_reset_tokens (token, username, email, expiry_time, used)
                VALUES (?, ?, ?, ?, FALSE)
                """, token, user.username(), user.email(), Timestamp.from(expiryTime));

        String cleanBaseUrl = (baseUrl != null && baseUrl.endsWith("/")) ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String verificationLink = cleanBaseUrl + "/reset-password?token=" + token;

        emailService.sendPasswordResetEmail(user.email(), user.displayName(), verificationLink);
        LOGGER.info("Password reset token generated for user '{}', expires at {}", user.username(), expiryTime);

        return new ResetRequestResult(true, "A verification link has been sent to your email address.",
                user.email(), token, verificationLink);
    }

    public Optional<ResetTokenDetails> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            List<ResetTokenDetails> tokens = jdbc.query("""
                    SELECT t.token, t.username, t.email, t.expiry_time, t.used, u.display_name
                    FROM password_reset_tokens t
                    JOIN app_users u ON t.username = u.username
                    WHERE t.token = ?
                    """, (rs, rowNum) -> new ResetTokenDetails(
                    rs.getString("token"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getTimestamp("expiry_time").toInstant(),
                    rs.getBoolean("used"),
                    rs.getString("display_name")
            ), token.trim());

            if (tokens.isEmpty()) {
                return Optional.empty();
            }

            ResetTokenDetails details = tokens.get(0);
            if (details.used() || details.expiryTime().isBefore(Instant.now())) {
                return Optional.empty();
            }

            return Optional.of(details);
        } catch (Exception ex) {
            LOGGER.error("Error validating reset token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public ResetCompletionResult completePasswordReset(String token, String newPassword, String confirmPassword) {
        if (token == null || token.isBlank()) {
            return new ResetCompletionResult(false, "Invalid or missing verification token.");
        }

        Optional<ResetTokenDetails> tokenOpt = validateToken(token);
        if (tokenOpt.isEmpty()) {
            return new ResetCompletionResult(false, "The password reset link is invalid, expired, or has already been used. Please request a new link.");
        }

        if (newPassword == null || confirmPassword == null) {
            return new ResetCompletionResult(false, "Password cannot be empty.");
        }

        if (!newPassword.equals(confirmPassword)) {
            return new ResetCompletionResult(false, "Passwords do not match. Please verify both fields.");
        }

        if (newPassword.length() < 12 || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return new ResetCompletionResult(false, "Password must be between 12 and 72 characters long.");
        }

        ResetTokenDetails details = tokenOpt.get();
        String passwordHash = passwordEncoder.encode(newPassword);

        // Update password in app_users
        int userUpdated = jdbc.update("UPDATE app_users SET password_hash = ? WHERE username = ?", passwordHash, details.username());
        if (userUpdated == 0) {
            return new ResetCompletionResult(false, "User account could not be found.");
        }

        // Mark token as used
        jdbc.update("UPDATE password_reset_tokens SET used = TRUE WHERE token = ?", token.trim());
        LOGGER.info("Password successfully reset for user '{}'", details.username());

        return new ResetCompletionResult(true, "Password has been successfully changed. You can now log in with your new credentials.");
    }

    public record UserRecord(String username, String displayName, String email, boolean enabled) {}
    public record ResetTokenDetails(String token, String username, String email, Instant expiryTime, boolean used, String displayName) {}
    public record ResetRequestResult(boolean success, String message, String email, String token, String verificationLink) {}
    public record ResetCompletionResult(boolean success, String message) {}
}
