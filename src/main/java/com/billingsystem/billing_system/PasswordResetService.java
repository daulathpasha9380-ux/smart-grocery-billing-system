package com.billingsystem.billing_system;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final long TOKEN_LIFETIME_SECONDS = 30 * 60;
    private static final long REQUEST_COOLDOWN_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String mailFrom;
    private final String publicBaseUrl;

    public PasswordResetService(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from}") String mailFrom,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (mailHost.isBlank()) {
            throw new PasswordResetConfigurationException(
                    "Password reset email is not configured. Set MAIL_HOST and SMTP settings.");
        }

        List<ResetAccount> accounts = jdbc.query("""
                SELECT username, email FROM app_users
                WHERE LOWER(email) = LOWER(?) AND enabled = TRUE
                """, (rs, row) -> new ResetAccount(rs.getString("username"), rs.getString("email")), email.trim());
        if (accounts.isEmpty()) {
            return;
        }

        ResetAccount account = accounts.get(0);
        Timestamp cooldownStart = Timestamp.from(Instant.now().minusSeconds(REQUEST_COOLDOWN_SECONDS));
        Integer recentRequests = jdbc.queryForObject("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE username = ? AND created_at > ?
                """, Integer.class, account.username(), cooldownStart);
        if (recentRequests != null && recentRequests > 0) {
            return;
        }

        jdbc.update("DELETE FROM password_reset_tokens WHERE username = ?", account.username());
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO password_reset_tokens (token_hash, username, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, hashToken(token), account.username(), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(TOKEN_LIFETIME_SECONDS)));

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new PasswordResetConfigurationException(
                    "Password reset email is not configured. Set MAIL_HOST and SMTP settings.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(account.email());
        message.setSubject("Reset your FreshLedger password");
        message.setText("""
                A password reset was requested for your FreshLedger account.

                Use this link within 30 minutes to choose a new password:
                %s/reset-password?token=%s

                If you did not request this, you can ignore this email.
                """.formatted(publicBaseUrl, URLEncoder.encode(token, StandardCharsets.UTF_8)));
        mailSender.send(message);
    }

    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP
                """, Integer.class, hashToken(token));
        return count != null && count == 1;
    }

    public int minimumPasswordLengthForToken(String token) {
        if (token == null || token.isBlank()) {
            return PasswordPolicy.minimumLength("OWNER");
        }
        List<String> roles = jdbc.query("""
                SELECT app_users.role
                FROM password_reset_tokens
                JOIN app_users ON app_users.username = password_reset_tokens.username
                WHERE password_reset_tokens.token_hash = ?
                  AND password_reset_tokens.expires_at > CURRENT_TIMESTAMP
                  AND app_users.enabled = TRUE
                """, (rs, row) -> rs.getString("role"), hashToken(token));
        return roles.isEmpty() ? PasswordPolicy.minimumLength("OWNER")
                : PasswordPolicy.minimumLength(roles.get(0));
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            return false;
        }
        List<String> usernames = jdbc.query("""
                SELECT username FROM password_reset_tokens
                WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP
                FOR UPDATE
                """, (rs, row) -> rs.getString("username"), hashToken(token));
        if (usernames.isEmpty()) {
            return false;
        }

        String username = usernames.get(0);
        int updated = jdbc.update("""
                UPDATE app_users SET password_hash = ? WHERE username = ? AND enabled = TRUE
                """, passwordEncoder.encode(newPassword), username);
        if (updated != 1) {
            return false;
        }
        jdbc.update("DELETE FROM password_reset_tokens WHERE username = ?", username);
        return true;
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record ResetAccount(String username, String email) {}

    public static class PasswordResetConfigurationException extends RuntimeException {
        public PasswordResetConfigurationException(String message) {
            super(message);
        }
    }
}
