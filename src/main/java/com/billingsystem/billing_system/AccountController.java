package com.billingsystem.billing_system;

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
        if (request == null || request.currentPassword() == null || request.newPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide the current password and a valid new password.");
        }

        List<PasswordAccount> accounts = jdbc.query("""
                SELECT password_hash, role FROM app_users WHERE username = ? AND enabled = TRUE
                """, (rs, row) -> new PasswordAccount(rs.getString("password_hash"), rs.getString("role")),
                principal.getName());
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }
        PasswordAccount account = accounts.get(0);
        if (!PasswordPolicy.isValid(request.newPassword(), account.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new password does not meet the requirements.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), account.passwordHash())) {
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

    private record PasswordAccount(String passwordHash, String role) {}
}
