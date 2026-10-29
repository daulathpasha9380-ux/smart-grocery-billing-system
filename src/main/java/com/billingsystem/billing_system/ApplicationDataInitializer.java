package com.billingsystem.billing_system;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class ApplicationDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String ownerPassword;
    private final String cashierPassword;
    private final String customerPassword;
    private final String ownerEmail;
    private final String cashierEmail;

    public ApplicationDataInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.owner-password:}") String ownerPassword,
            @Value("${app.auth.cashier-password:}") String cashierPassword,
            @Value("${app.auth.customer-password:}") String customerPassword,
            @Value("${app.auth.owner-email:}") String ownerEmail,
            @Value("${app.auth.cashier-email:}") String cashierEmail) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.ownerPassword = ownerPassword;
        this.cashierPassword = cashierPassword;
        this.customerPassword = customerPassword;
        this.ownerEmail = ownerEmail;
        this.cashierEmail = cashierEmail;
    }

    @Override
    public void run(String... args) {
        seedOwner();
        seedOptionalUser("cashier", cashierPassword, "CASHIER", "Store Cashier", null, null, 0,
                "APP_AUTH_CASHIER_PASSWORD");
        setRecoveryEmail("owner", ownerEmail, "APP_AUTH_OWNER_EMAIL");
        setRecoveryEmail("cashier", cashierEmail, "APP_AUTH_CASHIER_EMAIL");
        seedOptionalUser("customer", customerPassword, "CUSTOMER", "Sample Customer",
                "customer@freshledger.local", "+91 98765 43210", 1250, "APP_AUTH_CUSTOMER_PASSWORD");
        List<ProductImage> missingImages = jdbc.query("SELECT id, name, category FROM products WHERE image_path IS NULL",
                (rs, row) -> new ProductImage(rs.getLong("id"), rs.getString("name"), rs.getString("category")));
        for (ProductImage product : missingImages) {
            jdbc.update("UPDATE products SET image_path = ? WHERE id = ? AND image_path IS NULL",
                    ProductImageLibrary.match(product.name(), product.category()), product.id());
        }
    }

    private void seedOwner() {
        if (seededUserRole("owner", "OWNER")) {
            return;
        }
        Integer owners = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        if (owners != null && owners > 0) {
            return;
        }
        insertSeededUser("owner", ownerPassword, "OWNER", "Store Owner", null, null, 0,
                "APP_AUTH_OWNER_PASSWORD");
    }

    private void seedOptionalUser(String username, String rawPassword, String role, String displayName,
                                 String email, String phone, int loyaltyPoints, String environmentVariable) {
        if (seededUserRole(username, role) || rawPassword.isBlank()) {
            return;
        }
        insertSeededUser(username, rawPassword, role, displayName, email, phone, loyaltyPoints,
                environmentVariable);
    }

    private boolean seededUserRole(String username, String expectedRole) {
        List<String> roles = jdbc.query(
                "SELECT role FROM app_users WHERE username = ?",
                (rs, row) -> rs.getString("role"), username);
        if (roles.isEmpty()) {
            return false;
        }
        if (!expectedRole.equals(roles.get(0))) {
            throw new IllegalStateException("The seeded username '" + username
                    + "' already exists with a different role.");
        }
        return true;
    }

    private void insertSeededUser(String username, String rawPassword, String role, String displayName,
                                  String email, String phone, int loyaltyPoints, String environmentVariable) {
        if (rawPassword.isBlank()) {
            throw new IllegalStateException("Set " + environmentVariable
                    + " (" + PasswordPolicy.minimumLength(role)
                    + " to 72 UTF-8 bytes) to create the initial " + role.toLowerCase() + " account.");
        }
        if (!PasswordPolicy.isValid(rawPassword, role)) {
            throw new IllegalStateException(environmentVariable + " must be "
                    + PasswordPolicy.minimumLength(role) + " to 72 UTF-8 bytes long.");
        }
        jdbc.update("""
                INSERT INTO app_users (username, password_hash, role, display_name, email, phone, loyalty_points)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, username, passwordEncoder.encode(rawPassword), role, displayName, email, phone, loyaltyPoints);
    }

    private void setRecoveryEmail(String username, String email, String environmentVariable) {
        if (email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || normalizedEmail.length() > 254) {
            throw new IllegalStateException(environmentVariable + " must be a valid email address.");
        }
        try {
            jdbc.update("UPDATE app_users SET email = ? WHERE username = ? AND email IS NULL",
                    normalizedEmail, username);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new IllegalStateException(environmentVariable
                    + " is already assigned to another account.", exception);
        }
    }

    private record ProductImage(long id, String name, String category) {}
}
