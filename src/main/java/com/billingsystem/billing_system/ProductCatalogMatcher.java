package com.billingsystem.billing_system;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProductCatalogMatcher {

    public record CatalogProduct(
            long id,
            String name,
            String brand,
            String category,
            String barcode,
            BigDecimal price,
            int stock,
            String imagePath
    ) {}

    public record ProductMatch(CatalogProduct product, double score) {}

    public static List<ProductMatch> match(String query, List<CatalogProduct> catalog) {
        if (query == null || query.isBlank() || catalog == null) {
            return List.of();
        }
        String cleanQuery = query.toLowerCase(Locale.ROOT).trim();
        String[] queryWords = cleanQuery.split("\\s+");

        List<ProductMatch> results = new ArrayList<>();
        for (CatalogProduct p : catalog) {
            String pName = p.name() != null ? p.name().toLowerCase(Locale.ROOT) : "";
            String pBrand = p.brand() != null ? p.brand().toLowerCase(Locale.ROOT) : "";
            String pCategory = p.category() != null ? p.category().toLowerCase(Locale.ROOT) : "";
            String pBarcode = p.barcode() != null ? p.barcode().trim() : "";

            double score = 0.0;
            if (pBarcode.equalsIgnoreCase(cleanQuery)) {
                score = 100.0;
            } else if (pName.equalsIgnoreCase(cleanQuery)) {
                score = 90.0;
            } else if (pName.contains(cleanQuery)) {
                score = 70.0;
            } else {
                int matched = 0;
                for (String word : queryWords) {
                    if (word.length() >= 2 && (pName.contains(word) || pBrand.contains(word) || pCategory.contains(word))) {
                        matched++;
                    }
                }
                if (matched > 0) {
                    score = 20.0 * matched;
                }
            }

            if (score > 0.0) {
                results.add(new ProductMatch(p, score));
            }
        }
        results.sort(Comparator.comparingDouble(ProductMatch::score).reversed());
        return results;
    }
}
