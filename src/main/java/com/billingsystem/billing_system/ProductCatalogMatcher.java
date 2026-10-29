package com.billingsystem.billing_system;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProductCatalogMatcher {
    private static final Set<String> IGNORED_WORDS = Set.of(
            "add", "find", "search", "for", "the", "of", "my", "cart", "please",
            "packet", "packets", "pack", "packs", "piece", "pieces", "item", "items",
            "kilogram", "kilograms", "kilo", "kilos", "kg", "kgs", "gram", "grams", "g",
            "litre", "litres", "liter", "liters", "l");

    private ProductCatalogMatcher() {}

    public static List<ProductMatch> match(String query, List<CatalogProduct> products) {
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) return List.of();
        List<ProductMatch> matches = new ArrayList<>();
        for (CatalogProduct product : products) {
            Set<String> catalogTokens = tokens(product.name() + " " + product.brand() + " " + product.category());
            long overlap = queryTokens.stream().filter(catalogTokens::contains).count();
            if (overlap == 0) continue;

            double tokenScore = (double) overlap / queryTokens.size();
            String normalizedQuery = normalize(query);
            String normalizedName = normalize(product.name());
            boolean phraseMatch = normalizedName.contains(normalizedQuery)
                    || normalize(product.brand()).equals(normalizedQuery);
            double confidence = Math.min(1d, tokenScore * 0.8d + (phraseMatch ? 0.2d : 0d));
            if (confidence >= 0.32d) {
                matches.add(new ProductMatch(product, BigDecimal.valueOf(confidence)
                        .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue()));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(ProductMatch::confidence).reversed()
                        .thenComparing(match -> match.product().name()))
                .limit(5)
                .toList();
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String word : normalize(value).split("\\s+")) {
            if (word.isBlank() || IGNORED_WORDS.contains(word)) continue;
            result.add(singularize(word));
        }
        return result;
    }

    private static String singularize(String value) {
        if (value.length() > 4 && value.endsWith("ies")) return value.substring(0, value.length() - 3) + "y";
        if (value.length() > 3 && value.endsWith("s") && !value.endsWith("ss")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    public record CatalogProduct(
            long id, String name, String brand, String category, String barcode,
            BigDecimal price, int stock, String imagePath) {}

    public record ProductMatch(CatalogProduct product, double confidence) {}
}
