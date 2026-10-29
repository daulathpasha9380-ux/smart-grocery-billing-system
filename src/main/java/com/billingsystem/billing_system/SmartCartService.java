package com.billingsystem.billing_system;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SmartCartService {
    private final JdbcTemplate jdbc;

    public SmartCartService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CommandResult interpret(String input) {
        SmartCartCommandParser.ParsedCommand command = SmartCartCommandParser.parse(input);
        if (command == null) return null;
        List<ProductCatalogMatcher.ProductMatch> matches = List.of();
        if (command.query() != null && !command.query().isBlank()) {
            List<ProductCatalogMatcher.CatalogProduct> products = jdbc.query("""
                    SELECT id, name, brand, category, barcode, price, stock, image_path
                    FROM products WHERE active = TRUE
                    """, (rs, row) -> new ProductCatalogMatcher.CatalogProduct(
                    rs.getLong("id"), rs.getString("name"), rs.getString("brand"),
                    rs.getString("category"), rs.getString("barcode"), rs.getBigDecimal("price"),
                    rs.getInt("stock"), rs.getString("image_path")));
            matches = ProductCatalogMatcher.match(command.query(), products);
        }
        return new CommandResult(command.intent(), command.query(), command.quantity(), command.unit(),
                matches.stream().map(match -> ProductSuggestion.from(match.product(), match.confidence())).toList());
    }

    public record CommandResult(String intent, String query, int quantity, String unit,
                                List<ProductSuggestion> matches) {}

    public record ProductSuggestion(long id, String name, String brand, String category, double price,
                                    int stock, String imagePath, double confidence) {
        static ProductSuggestion from(ProductCatalogMatcher.CatalogProduct product, double confidence) {
            return new ProductSuggestion(product.id(), product.name(), product.brand(), product.category(),
                    product.price().doubleValue(), product.stock(), product.imagePath(), confidence);
        }
    }
}
