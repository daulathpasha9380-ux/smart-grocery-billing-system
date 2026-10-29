package com.billingsystem.billing_system;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public RegistrationController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String password,
            Model model) {
        String normalizedUsername = username.trim().toLowerCase();
        String normalizedEmail = email.trim().toLowerCase();
        if (!normalizedUsername.matches("[a-z0-9._-]{3,80}")
                || displayName.isBlank() || phone.isBlank()
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || !PasswordPolicy.isValid(password, "CUSTOMER")) {
            model.addAttribute("registrationError",
                    "Enter valid account details and a password of 8 to 72 UTF-8 bytes.");
            return "register";
        }
        try {
            jdbc.update("""
                    INSERT INTO app_users (username, password_hash, role, display_name, email, phone)
                    VALUES (?, ?, 'CUSTOMER', ?, ?, ?)
                    """, normalizedUsername, passwordEncoder.encode(password), displayName.trim(),
                    normalizedEmail, phone.trim());
        } catch (DuplicateKeyException exception) {
            model.addAttribute("registrationError",
                    "That username or email is already registered. Please choose another.");
            return "register";
        }
        return "redirect:/login?registered";
    }
}
