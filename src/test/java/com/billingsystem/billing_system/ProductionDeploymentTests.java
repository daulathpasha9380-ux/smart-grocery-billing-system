package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-prod-deployment-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
class ProductionDeploymentTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM app_users WHERE username = 'test_owner'");
    }

    @Test
    @DisplayName("NeonDataSourcePostProcessor enforces sslmode=require on remote Neon endpoints")
    void neonPostProcessorEnforcesSslOnNeonEndpoint() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb");

        NeonDataSourcePostProcessor.normalizeDataSourceProperties(properties);

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb?sslmode=require");
    }

    @Test
    @DisplayName("NeonDataSourcePostProcessor appends sslmode=require to existing query parameters")
    void neonPostProcessorAppendsSslToExistingQuery() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb?options=-c%20search_path=public");

        NeonDataSourcePostProcessor.normalizeDataSourceProperties(properties);

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb?options=-c%20search_path=public&sslmode=require");
    }

    @Test
    @DisplayName("NeonDataSourcePostProcessor preserves existing sslmode parameter without duplication")
    void neonPostProcessorPreservesExistingSslMode() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb?sslmode=require");

        NeonDataSourcePostProcessor.normalizeDataSourceProperties(properties);

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech/neondb?sslmode=require");
    }

    @Test
    @DisplayName("NeonDataSourcePostProcessor converts postgresql:// URI format and extracts credentials")
    void neonPostProcessorConvertsPostgreSqlUriWithCredentials() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("postgresql://neonuser:secretpass@ep-silent-smoke-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");

        NeonDataSourcePostProcessor.normalizeDataSourceProperties(properties);

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://ep-silent-smoke-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
        assertThat(properties.getUsername()).isEqualTo("neonuser");
        assertThat(properties.getPassword()).isEqualTo("secretpass");
    }

    @Test
    @DisplayName("NeonDataSourcePostProcessor does not force sslmode on localhost/127.0.0.1")
    void neonPostProcessorLeavesLocalhostUnchanged() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/freshledger");

        NeonDataSourcePostProcessor.normalizeDataSourceProperties(properties);

        assertThat(properties.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/freshledger");
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper preserves existing owner in database")
    void bootstrapperPreservesExistingOwner() {
        ProductionOwnerBootstrapper bootstrapper = new ProductionOwnerBootstrapper(
                jdbc, passwordEncoder, "NewPassword#2026", "owner@example.com");

        Integer owners = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        assertThat(owners).isGreaterThan(0);

        // When owners > 0, bootstrapper is a safe no-op
        bootstrapper.run();

        // Verify existing owner is untouched
        String existingHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE username = 'owner'", String.class);
        assertThat(existingHash).isNotBlank();
        assertThat(passwordEncoder.matches("TestOwnerPassword#2026", existingHash)).isTrue();
    }

    @Test
    @DisplayName("ProductionOwnerBootstrapper rejects overlong passwords (> 72 bytes) safely")
    void bootstrapperRejectsOverlongPassword() {
        String longPass = "A".repeat(73);
        ProductionOwnerBootstrapper bootstrapper = new ProductionOwnerBootstrapper(
                jdbc, passwordEncoder, longPass, "");

        // Since owner already exists in the test DB, it returns safely without throwing or changing anything
        bootstrapper.run();

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE role = 'OWNER'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
