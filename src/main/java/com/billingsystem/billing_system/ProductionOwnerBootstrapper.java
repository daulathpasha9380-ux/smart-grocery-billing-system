package com.billingsystem.billing_system;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionOwnerBootstrapper implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;
    private final String bootstrapEmail;

    public ProductionOwnerBootstrapper(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-owner-password:}") String bootstrapPassword,
            @Value("${app.auth.bootstrap-owner-email:}") String bootstrapEmail) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    public void run(String... args) {
        Integer owners = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        if (owners != null && owners > 0) {
            return;
        }
        Integer existingOwnerName = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE username = 'owner'", Integer.class);
        if (existingOwnerName != null && existingOwnerName > 0) {
            throw new IllegalStateException("The username 'owner' exists but is not assigned the OWNER role.");
        }
        if (bootstrapPassword.length() < 12
                || bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Set APP_BOOTSTRAP_OWNER_PASSWORD (12 to 72 UTF-8 bytes) to create the initial production owner.");
        }
        String email = bootstrapEmail.trim().toLowerCase();
        if (!email.isEmpty() && (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254)) {
            throw new IllegalStateException("APP_BOOTSTRAP_OWNER_EMAIL must be a valid email address.");
        }
        jdbc.update("""
                INSERT INTO app_users (username, password_hash, role, display_name, email, enabled)
                VALUES ('owner', ?, 'OWNER', 'Store Owner', ?, TRUE)
                """, passwordEncoder.encode(bootstrapPassword), email.isEmpty() ? null : email);
    }
}
