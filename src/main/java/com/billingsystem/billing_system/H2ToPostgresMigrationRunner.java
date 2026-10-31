package com.billingsystem.billing_system;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("prod")
@ConditionalOnProperty(name = "app.migration.h2-to-postgres.enabled", havingValue = "true")
@DependsOnDatabaseInitialization
@Order(1)
public class H2ToPostgresMigrationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(H2ToPostgresMigrationRunner.class);
    private static final int BATCH_SIZE = 500;
    private static final List<String> TABLES = List.of(
            "app_users", "products", "transactions", "transaction_items");
    private static final Map<String, List<String>> TABLE_COLUMNS = Map.of(
            "app_users", List.of("username", "password_hash", "role", "display_name", "email", "phone",
                    "loyalty_points"),
            "products", List.of("id", "name", "category", "brand", "barcode", "purchase_price", "price",
                    "stock", "min_stock", "active", "image_path"),
            "transactions", List.of("id", "operation_id", "invoice", "transaction_type", "customer_username",
                    "customer_name", "cashier_username", "payment_mode", "subtotal", "discount", "tax", "total",
                    "status", "created_at"),
            "transaction_items", List.of("id", "transaction_id", "product_id", "product_name", "unit_price",
                    "quantity", "line_total"));

    private final JdbcTemplate target;
    private final TransactionTemplate transactionTemplate;
    private final String sourceUrl;
    private final String sourceUsername;
    private final String sourcePassword;

    public H2ToPostgresMigrationRunner(
            DataSource targetDataSource,
            TransactionTemplate transactionTemplate,
            @Value("${app.migration.h2-to-postgres.source-url:}") String sourceUrl,
            @Value("${app.migration.h2-to-postgres.source-username:sa}") String sourceUsername,
            @Value("${app.migration.h2-to-postgres.source-password:}") String sourcePassword) {
        this.target = new JdbcTemplate(targetDataSource);
        this.transactionTemplate = transactionTemplate;
        this.sourceUrl = sourceUrl;
        this.sourceUsername = sourceUsername;
        this.sourcePassword = sourcePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!sourceUrl.startsWith("jdbc:h2:")
                || !sourceUrl.toUpperCase(Locale.ROOT).contains("ACCESS_MODE_DATA=R")) {
            throw new IllegalStateException(
                    "H2_MIGRATION_URL must point to a read-only H2 database URL using ACCESS_MODE_DATA=R.");
        }

        try (Connection source = DriverManager.getConnection(sourceUrl, sourceUsername, sourcePassword)) {
            source.setReadOnly(true);
            Map<String, Long> expectedCounts = readTableCounts(source);
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    ensureTargetIsEmpty();
                    for (String table : TABLES) {
                        copyTable(source, table, TABLE_COLUMNS.get(table));
                    }
                    resetIdentitySequences();
                    verifyMigration(source, expectedCounts);
                } catch (SQLException exception) {
                    throw new IllegalStateException("The H2 to PostgreSQL migration failed.", exception);
                }
            });
            LOGGER.info("H2 to PostgreSQL migration completed and verified. Imported row counts: {}",
                    expectedCounts);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read the H2 migration source.", exception);
        }
    }

    private Map<String, Long> readTableCounts(Connection source) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            try (var statement = source.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                result.next();
                counts.put(table, result.getLong(1));
            }
        }
        return counts;
    }

    private void ensureTargetIsEmpty() {
        for (String table : TABLES) {
            Long count = target.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            if (count == null || count != 0) {
                throw new IllegalStateException(
                        "PostgreSQL table " + table + " is not empty; no H2 data was imported.");
            }
        }
    }

    private void copyTable(Connection source, String table, List<String> columns) throws SQLException {
        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String selectSql = "SELECT " + columnList + " FROM " + table;
        String insertSql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                Object[] values = new Object[columns.size()];
                for (int index = 0; index < columns.size(); index++) {
                    values[index] = rows.getObject(index + 1);
                }
                batch.add(values);
                if (batch.size() == BATCH_SIZE) {
                    target.batchUpdate(insertSql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            target.batchUpdate(insertSql, batch);
        }
    }

    private void resetIdentitySequences() {
        for (String table : List.of("products", "transactions", "transaction_items")) {
            target.queryForObject("""
                    SELECT setval(pg_get_serial_sequence(?, 'id'),
                        COALESCE((SELECT MAX(id) FROM %s), 1),
                        EXISTS (SELECT 1 FROM %s))
                    """.formatted(table, table), Long.class, table);
        }
    }

    private void verifyMigration(Connection source, Map<String, Long> expectedCounts) throws SQLException {
        for (String table : TABLES) {
            Long actual = target.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            if (!expectedCounts.get(table).equals(actual)) {
                throw new IllegalStateException("Row count mismatch for " + table + ": expected "
                        + expectedCounts.get(table) + ", got " + actual + ".");
            }
        }

        for (String column : List.of("subtotal", "discount", "tax", "total")) {
            BigDecimal sourceTotal = sourceDecimal(source, "SELECT COALESCE(SUM(" + column + "), 0) FROM transactions");
            BigDecimal targetTotal = target.queryForObject(
                    "SELECT COALESCE(SUM(" + column + "), 0) FROM transactions", BigDecimal.class);
            if (sourceTotal.compareTo(targetTotal) != 0) {
                throw new IllegalStateException("Financial total mismatch for " + column + ".");
            }
        }

        Map<Long, Integer> sourceStock = sourceStock(source);
        Map<Long, Integer> targetStock = target.query("""
                SELECT id, stock FROM products ORDER BY id
                """, rows -> {
            Map<Long, Integer> stock = new LinkedHashMap<>();
            while (rows.next()) {
                stock.put(rows.getLong("id"), rows.getInt("stock"));
            }
            return stock;
        });
        if (!sourceStock.equals(targetStock)) {
            throw new IllegalStateException("Product IDs or stock quantities differ after migration.");
        }

        assertNoOrphans(source, """
                SELECT COUNT(*) FROM transaction_items i
                LEFT JOIN transactions t ON t.id = i.transaction_id WHERE t.id IS NULL
                """, """
                SELECT COUNT(*) FROM transaction_items i
                LEFT JOIN transactions t ON t.id = i.transaction_id WHERE t.id IS NULL
                """, "invoice items");
        assertNoOrphans(source, """
                SELECT COUNT(*) FROM transactions t
                LEFT JOIN app_users u ON u.username = t.customer_username
                WHERE t.customer_username IS NOT NULL AND u.username IS NULL
                """, """
                SELECT COUNT(*) FROM transactions t
                LEFT JOIN app_users u ON u.username = t.customer_username
                WHERE t.customer_username IS NOT NULL AND u.username IS NULL
                """, "customer invoices");
        assertNoOrphans(source, """
                SELECT COUNT(*) FROM transactions t
                LEFT JOIN app_users u ON u.username = t.cashier_username
                WHERE u.username IS NULL
                """, """
                SELECT COUNT(*) FROM transactions t
                LEFT JOIN app_users u ON u.username = t.cashier_username
                WHERE u.username IS NULL
                """, "cashier invoices");
    }

    private Map<Long, Integer> sourceStock(Connection source) throws SQLException {
        Map<Long, Integer> stock = new LinkedHashMap<>();
        try (var statement = source.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, stock FROM products ORDER BY id")) {
            while (rows.next()) {
                stock.put(rows.getLong("id"), rows.getInt("stock"));
            }
        }
        return stock;
    }

    private void assertNoOrphans(Connection source, String sourceSql, String targetSql, String relation)
            throws SQLException {
        long sourceOrphans = sourceLong(source, sourceSql);
        Long targetOrphans = target.queryForObject(targetSql, Long.class);
        if (sourceOrphans != 0 || targetOrphans == null || targetOrphans != 0) {
            throw new IllegalStateException("Unresolved " + relation + " relationships were found.");
        }
    }

    private BigDecimal sourceDecimal(Connection source, String sql) throws SQLException {
        try (var statement = source.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getBigDecimal(1);
        }
    }

    private long sourceLong(Connection source, String sql) throws SQLException {
        try (var statement = source.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
