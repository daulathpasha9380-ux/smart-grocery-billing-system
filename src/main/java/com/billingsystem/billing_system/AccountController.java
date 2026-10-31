package com.billingsystem.billing_system;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AccountController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/password")
    public void changePassword(@RequestBody PasswordChangeRequest request, Principal principal) {
        if (request == null || request.currentPassword() == null || request.newPassword() == null
                || request.newPassword().length() < 12
                || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide the current password and a new password of 12 to 72 UTF-8 bytes.");
        }

        List<String> hashes = jdbc.query("""
                SELECT password_hash FROM app_users WHERE username = ? AND enabled = TRUE
                """, (rs, row) -> rs.getString("password_hash"), principal.getName());
        if (hashes.isEmpty() || !passwordEncoder.matches(request.currentPassword(), hashes.get(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }

        int updated = jdbc.update("""
                UPDATE app_users SET password_hash = ? WHERE username = ? AND enabled = TRUE
                """, passwordEncoder.encode(request.newPassword()), principal.getName());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The account changed while the password was being updated. Please try again.");
        }
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {}
}
