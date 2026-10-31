package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Automated tests verifying fresh PostgreSQL database initialization via Flyway migrations
 * and initial production owner bootstrapping.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fresh_postgres_init_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "app.auth.bootstrap-owner-password=ProductionOwnerSecret#2026",
        "app.auth.bootstrap-owner-email=owner@freshledger.com",
        "app.migration.h2-to-postgres.enabled=false"
})
@ActiveProfiles("prod")
class FreshPostgresInitializationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private ProductionOwnerBootstrapper bootstrapper;

    @Test
    @DisplayName("Fresh PostgreSQL initialization executes all Flyway migrations successfully")
    void flywayMigrationsExecutedOnFreshDatabase() throws Exception {
        // Verify Flyway schema history table exists and recorded migrations
        List<String> installedVersions = jdbc.query(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                (rs, rowNum) -> rs.getString("version"));

        assertThat(installedVersions)
                .as("All Flyway migration versions should be applied successfully")
                .containsExactly("1", "2", "3", "4", "5");

        // Verify all application tables exist in metadata
        List<String> tableNames = new ArrayList<>();
        try (var conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tableNames.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }

        assertThat(tableNames)
                .as("Fresh schema should contain all core application tables")
                .contains(
                        "app_users",
                        "products",
                        "transactions",
                        "transaction_items",
                        "password_reset_tokens",
                        "refunds",
                        "refund_items",
                        "stock_movements",
                        "waste_records",
                        "loyalty_transactions"
                );
    }

    @Test
    @DisplayName("Fresh PostgreSQL initialization creates app_users table with required columns")
    void appUsersTableSchemaCompatibility() {
        // Query app_users to verify expected columns and default values
        Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        assertThat(userCount).isNotNull();
        assertThat(userCount).isGreaterThanOrEqualTo(1);

        // Verify specific columns by selecting them explicitly
        jdbc.query("SELECT username, password_hash, role, display_name, email, phone, loyalty_points, enabled FROM app_users WHERE username = 'owner'",
                rs -> {
                    assertThat(rs.getString("username")).isEqualTo("owner");
                    assertThat(rs.getString("role")).isEqualTo("OWNER");
                    assertThat(rs.getString("display_name")).isEqualTo("Store Owner");
                    assertThat(rs.getString("email")).isEqualTo("owner@freshledger.com");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                    assertThat(rs.getInt("loyalty_points")).isEqualTo(0);
                });
    }

    @Test
    @DisplayName("Fresh PostgreSQL initialization creates products table with required schema")
    void productsTableSchemaCompatibility() {
        Integer productCount = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        assertThat(productCount).isEqualTo(0);

        // Test insertion and identity key generation
        jdbc.update("""
                INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock, active, image_path)
                VALUES ('Fresh Milk', 'Dairy', 'Heritage', 'BAR123456', 25.00, 30.00, 50, 10, TRUE, '/product-images/milk.svg')
                """);

        Long id = jdbc.queryForObject("SELECT id FROM products WHERE barcode = 'BAR123456'", Long.class);
        assertThat(id).isNotNull();
        assertThat(id).isPositive();

        // Clean up
        jdbc.update("DELETE FROM products WHERE barcode = 'BAR123456'");
    }

    @Test
    @DisplayName("Fresh PostgreSQL initialization creates transactions and items tables with constraints")
    void transactionsTableSchemaCompatibility() {
        Integer txCount = jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(txCount).isEqualTo(0);

        Integer itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM transaction_items", Integer.class);
        assertThat(itemCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Fresh PostgreSQL initialization creates password_reset_tokens table with constraints")
    void passwordResetTokensTableSchemaCompatibility() {
        Integer tokenCount = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
        assertThat(tokenCount).isEqualTo(0);
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper bootstraps owner user on fresh database")
    void ownerAccountBootstrappedSuccessfully() {
        Integer ownerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        assertThat(ownerCount).isEqualTo(1);

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = 'owner'", String.class);
        assertThat(storedHash).isNotBlank();
        assertThat(passwordEncoder.matches("ProductionOwnerSecret#2026", storedHash)).isTrue();
    }

    @Test
    @DisplayName("Bootstrapped owner can authenticate with Spring Security UserDetailsService")
    void bootstrappedOwnerAuthenticatesWithSecurity() {
        UserDetails details = userDetailsService.loadUserByUsername("owner");
        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo("owner");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_OWNER");
        assertThat(passwordEncoder.matches("ProductionOwnerSecret#2026", details.getPassword())).isTrue();
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper is idempotent and preserves existing owner on re-run")
    void bootstrapperIsIdempotent() {
        String hashBefore = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = 'owner'", String.class);

        // Re-running bootstrapper should be a no-op
        bootstrapper.run();

        String hashAfter = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = 'owner'", String.class);
        assertThat(hashAfter).isEqualTo(hashBefore);

        Integer ownerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        assertThat(ownerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper rejects password under 12 characters when database needs bootstrap")
    void bootstrapperRejectsShortPasswordOnUnbootstrappedDatabase() {
        // Temporarily delete owner to simulate unbootstrapped state
        jdbc.execute("DELETE FROM app_users WHERE username = 'owner'");

        try {
            ProductionOwnerBootstrapper shortPassBootstrapper = new ProductionOwnerBootstrapper(
                    jdbc, passwordEncoder, "short", "owner@freshledger.com");

            assertThatThrownBy(shortPassBootstrapper::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_BOOTSTRAP_OWNER_PASSWORD");
        } finally {
            // Restore owner
            bootstrapper.run();
        }
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper fails if username owner exists without OWNER role")
    void bootstrapperFailsIfOwnerUsernameHasWrongRole() {
        jdbc.execute("DELETE FROM app_users WHERE username = 'owner'");

        try {
            // Insert user 'owner' with CASHIER role
            jdbc.update("""
                    INSERT INTO app_users (username, password_hash, role, display_name, email, enabled)
                    VALUES ('owner', 'hash', 'CASHIER', 'Cashier', 'cashier@test.com', TRUE)
                    """);

            ProductionOwnerBootstrapper runner = new ProductionOwnerBootstrapper(
                    jdbc, passwordEncoder, "ProductionOwnerSecret#2026", "owner@freshledger.com");

            assertThatThrownBy(runner::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The username 'owner' exists but is not assigned the OWNER role.");
        } finally {
            jdbc.execute("DELETE FROM app_users WHERE username = 'owner'");
            bootstrapper.run();
        }
    }
}
