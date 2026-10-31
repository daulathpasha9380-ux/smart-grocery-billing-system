package com.billingsystem.billing_system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VoiceSmartCartService {

    public enum VoiceIntent {
        ADD_PRODUCT,
        REMOVE_PRODUCT,
        UPDATE_QUANTITY,
        SEARCH_PRODUCT,
        VIEW_CART,
        GET_TOTAL,
        CLEAR_CART,
        UNKNOWN
    }

    public record VoiceCommandResult(
            String rawCommand,
            VoiceIntent intent,
            int quantity,
            String productQuery,
            BillingDataController.ProductView product,
            List<BillingDataController.ProductView> candidates,
            boolean ambiguous,
            String feedback,
            boolean success
    ) {}

    private static final Map<String, Integer> NUMBER_WORDS = new HashMap<>();
    static {
        // English
        NUMBER_WORDS.put("zero", 0);
        NUMBER_WORDS.put("one", 1);
        NUMBER_WORDS.put("single", 1);
        NUMBER_WORDS.put("a", 1);
        NUMBER_WORDS.put("an", 1);
        NUMBER_WORDS.put("two", 2);
        NUMBER_WORDS.put("couple", 2);
        NUMBER_WORDS.put("double", 2);
        NUMBER_WORDS.put("pair", 2);
        NUMBER_WORDS.put("three", 3);
        NUMBER_WORDS.put("four", 4);
        NUMBER_WORDS.put("five", 5);
        NUMBER_WORDS.put("six", 6);
        NUMBER_WORDS.put("seven", 7);
        NUMBER_WORDS.put("eight", 8);
        NUMBER_WORDS.put("nine", 9);
        NUMBER_WORDS.put("ten", 10);
        NUMBER_WORDS.put("eleven", 11);
        NUMBER_WORDS.put("twelve", 12);
        NUMBER_WORDS.put("dozen", 12);
        NUMBER_WORDS.put("fifteen", 15);
        NUMBER_WORDS.put("twenty", 20);

        // Hindi
        NUMBER_WORDS.put("ek", 1);
        NUMBER_WORDS.put("do", 2);
        NUMBER_WORDS.put("teen", 3);
        NUMBER_WORDS.put("char", 4);
        NUMBER_WORDS.put("chaar", 4);
        NUMBER_WORDS.put("paanch", 5);
        NUMBER_WORDS.put("panch", 5);
        NUMBER_WORDS.put("chhe", 6);
        NUMBER_WORDS.put("saat", 7);
        NUMBER_WORDS.put("aath", 8);
        NUMBER_WORDS.put("nau", 9);
        NUMBER_WORDS.put("dus", 10);

        // Kannada
        NUMBER_WORDS.put("ondu", 1);
        NUMBER_WORDS.put("eradu", 2);
        NUMBER_WORDS.put("mooru", 3);
        NUMBER_WORDS.put("naalku", 4);
        NUMBER_WORDS.put("aidu", 5);
        NUMBER_WORDS.put("aaru", 6);
        NUMBER_WORDS.put("yelu", 7);
        NUMBER_WORDS.put("entu", 8);
        NUMBER_WORDS.put("ombattu", 9);
        NUMBER_WORDS.put("hattu", 10);
    }

    private static final Set<String> UNIT_WORDS = Set.of(
            "packet", "packets", "pkt", "pkts", "pack", "packs",
            "kg", "kgs", "kilogram", "kilograms", "kilo", "kilos",
            "gram", "grams", "gm", "gms", "g",
            "litre", "litres", "liter", "liters", "l", "ml",
            "bottle", "bottles", "btl",
            "box", "boxes", "can", "cans", "bag", "bags",
            "piece", "pieces", "pc", "pcs", "item", "items", "unit", "units",
            "pau", "aadha"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "please", "can", "you", "to", "the", "a", "an", "of", "in", "my", "me", "for", "from",
            "karo", "kijiye", "bhi", "aur"
    );

    private final JdbcTemplate jdbc;

    public VoiceSmartCartService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Parses a spoken or typed voice shopping command.
     */
    public VoiceCommandResult processCommand(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new VoiceCommandResult("", VoiceIntent.UNKNOWN, 0, "", null,
                    Collections.emptyList(), false, "Please speak or type a shopping command.", false);
        }

        String normalized = normalize(rawInput);

        // 1. Detect Intent
        VoiceIntent intent = detectIntent(normalized);

        // 2. Extract Quantity and Product Query based on Intent
        int quantity = 1;
        String productQuery = "";

        switch (intent) {
            case VIEW_CART -> {
                return new VoiceCommandResult(rawInput, intent, 0, "", null,
                        Collections.emptyList(), false, "Opening your cart.", true);
            }
            case GET_TOTAL -> {
                return new VoiceCommandResult(rawInput, intent, 0, "", null,
                        Collections.emptyList(), false, "Checking total bill amount.", true);
            }
            case CLEAR_CART -> {
                return new VoiceCommandResult(rawInput, intent, 0, "", null,
                        Collections.emptyList(), false, "Clearing all items from cart.", true);
            }
            case SEARCH_PRODUCT -> {
                productQuery = extractSearchQuery(normalized);
                List<BillingDataController.ProductView> candidates = findMatchingProducts(productQuery);
                return new VoiceCommandResult(rawInput, intent, 1, productQuery,
                        candidates.isEmpty() ? null : candidates.get(0),
                        candidates, candidates.size() > 1,
                        candidates.isEmpty()
                                ? "No products found matching '" + productQuery + "'."
                                : "Found " + candidates.size() + " matching products for '" + productQuery + "'.",
                        !candidates.isEmpty());
            }
            case UPDATE_QUANTITY -> {
                ParsedQuantityAndProduct pq = extractUpdateQuantityAndProduct(normalized);
                quantity = pq.quantity;
                productQuery = pq.product;
            }
            case REMOVE_PRODUCT -> {
                ParsedQuantityAndProduct pq = extractRemoveProduct(normalized);
                quantity = pq.quantity > 0 ? pq.quantity : 1;
                productQuery = pq.product;
            }
            case ADD_PRODUCT -> {
                ParsedQuantityAndProduct pq = extractAddProduct(normalized);
                quantity = pq.quantity > 0 ? pq.quantity : 1;
                productQuery = pq.product;
            }
            default -> {
                // If no specific verb was detected, check if it's a direct product mention like "two packets of milk" or "Amul milk"
                ParsedQuantityAndProduct pq = extractAddProduct("add " + normalized);
                if (!pq.product.isBlank()) {
                    List<BillingDataController.ProductView> matches = findMatchingProducts(pq.product);
                    if (!matches.isEmpty()) {
                        intent = VoiceIntent.ADD_PRODUCT;
                        quantity = pq.quantity > 0 ? pq.quantity : 1;
                        productQuery = pq.product;
                    }
                }
                if (intent == VoiceIntent.UNKNOWN) {
                    return new VoiceCommandResult(rawInput, VoiceIntent.UNKNOWN, 0, "", null,
                            Collections.emptyList(), false,
                            "Command not recognized. Try 'add two packets of milk' or type a supported command.", false);
                }
            }
        }

        if (productQuery.isBlank()) {
            return new VoiceCommandResult(rawInput, intent, quantity, "", null,
                    Collections.emptyList(), false, "Could not determine the product name from your command.", false);
        }

        // 3. Resolve Product in Catalog
        List<BillingDataController.ProductView> matches = findMatchingProducts(productQuery);
        if (matches.isEmpty()) {
            return new VoiceCommandResult(rawInput, intent, quantity, productQuery, null,
                    Collections.emptyList(), false,
                    "No product found matching '" + productQuery + "' in the catalog.", false);
        }

        boolean ambiguous = matches.size() > 1 && isAmbiguous(matches, productQuery);
        BillingDataController.ProductView topProduct = matches.get(0);

        String feedback;
        if (ambiguous) {
            feedback = "Found multiple products matching '" + productQuery + "'. Please choose one.";
        } else if (intent == VoiceIntent.ADD_PRODUCT) {
            feedback = "Adding " + quantity + " × " + topProduct.name() + " to cart.";
        } else if (intent == VoiceIntent.REMOVE_PRODUCT) {
            feedback = "Removing " + topProduct.name() + " from cart.";
        } else if (intent == VoiceIntent.UPDATE_QUANTITY) {
            feedback = "Updating " + topProduct.name() + " quantity to " + quantity + ".";
        } else {
            feedback = "Found " + topProduct.name() + ".";
        }

        return new VoiceCommandResult(rawInput, intent, quantity, productQuery, topProduct,
                matches, ambiguous, feedback, true);
    }

    public String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\r\n\t]+", " ")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    VoiceIntent detectIntent(String text) {
        // Clear cart
        if (text.matches(".*\\b(clear|empty|reset)\\b.*\\b(cart|bill|basket)\\b.*")
                || text.equals("clear cart") || text.equals("empty cart")
                || text.matches(".*\\b(delete all|remove all)\\b.*")) {
            return VoiceIntent.CLEAR_CART;
        }

        // View cart
        if (text.matches(".*\\b(show|view|open|display|check|see)\\b.*\\b(cart|bill|basket)\\b.*")
                || text.equals("show my cart") || text.equals("view cart")) {
            return VoiceIntent.VIEW_CART;
        }

        // Get total
        if (text.matches(".*\\b(what is|what's|how much|check|get|tell me)\\b.*\\b(total|bill|amount|cost)\\b.*")
                || text.equals("what is my total bill") || text.equals("what is my total")
                || text.equals("get total") || text.equals("total bill")) {
            return VoiceIntent.GET_TOTAL;
        }

        // Search product
        if (text.startsWith("search for ") || text.startsWith("search ")
                || text.startsWith("find ") || text.startsWith("look for ")
                || text.startsWith("show me ") || text.startsWith("where is ")) {
            return VoiceIntent.SEARCH_PRODUCT;
        }

        // Update quantity
        if (text.matches(".*\\b(increase|decrease|change|set|update|make)\\b.*\\b(quantity|qty|count|to)\\b.*")) {
            return VoiceIntent.UPDATE_QUANTITY;
        }

        // Remove product
        if (text.startsWith("remove ") || text.startsWith("delete ")
                || text.startsWith("drop ") || text.startsWith("take out ")
                || text.matches(".*\\b(hatao|nikalo|tegeyiri)\\b.*")) {
            return VoiceIntent.REMOVE_PRODUCT;
        }

        // Add product
        if (text.startsWith("add ") || text.startsWith("put ") || text.startsWith("buy ")
                || text.startsWith("take ") || text.startsWith("get ") || text.startsWith("include ")
                || text.matches(".*\\b(jodo|daalo|serisi)\\b.*")) {
            return VoiceIntent.ADD_PRODUCT;
        }

        return VoiceIntent.UNKNOWN;
    }

    private String extractSearchQuery(String text) {
        String query = text;
        for (String prefix : List.of("search for ", "search ", "find ", "look for ", "show me ", "where is ")) {
            if (query.startsWith(prefix)) {
                query = query.substring(prefix.length()).trim();
                break;
            }
        }
        return cleanProductTokens(query);
    }

    public record ParsedQuantityAndProduct(int quantity, String product) {}

    private ParsedQuantityAndProduct extractAddProduct(String text) {
        String clean = text;
        for (String prefix : List.of("add ", "put ", "buy ", "take ", "get ", "include ")) {
            if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length()).trim();
                break;
            }
        }
        // Hindi suffix: "add karo" / "daalo" / "jodo"
        clean = clean.replaceAll("\\b(add karo|daalo|jodo|serisi)\\b", "").trim();

        return parseQuantityAndProduct(clean);
    }

    private ParsedQuantityAndProduct extractRemoveProduct(String text) {
        String clean = text;
        for (String prefix : List.of("remove ", "delete ", "drop ", "take out ")) {
            if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length()).trim();
                break;
            }
        }
        clean = clean.replaceAll("\\b(hatao|nikalo|tegeyiri)\\b", "").trim();
        return parseQuantityAndProduct(clean);
    }

    private ParsedQuantityAndProduct extractUpdateQuantityAndProduct(String text) {
        // e.g. "increase milk quantity to three"
        // e.g. "change milk to 3"
        // e.g. "set quantity of milk to 2"
        // e.g. "update milk to 4"
        int targetQty = 1;
        String productPart = text;

        // Try to find "to <number>"
        Pattern toPattern = Pattern.compile("\\bto\\s+([a-zA-Z0-9]+)\\b");
        Matcher toMatcher = toPattern.matcher(text);
        if (toMatcher.find()) {
            String qtyToken = toMatcher.group(1);
            targetQty = parseQuantityToken(qtyToken, 1);
            // remove "to <qty>" from string
            productPart = text.substring(0, toMatcher.start()).trim();
        }

        // Clean verbs from product part
        productPart = productPart.replaceAll("\\b(increase|decrease|change|set|update|make)\\b", "")
                .replaceAll("\\b(quantity|qty|count|of)\\b", "")
                .trim();

        return new ParsedQuantityAndProduct(targetQty, cleanProductTokens(productPart));
    }

    public ParsedQuantityAndProduct parseQuantityAndProduct(String text) {
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return new ParsedQuantityAndProduct(1, "");
        }

        int quantity = 1;
        int tokenIndex = 0;

        // Check if first token is a number
        Integer q = parseNumberToken(tokens[0]);
        if (q != null && q > 0) {
            quantity = q;
            tokenIndex = 1;
        }

        // Check if second token is unit or unit+number
        if (tokenIndex < tokens.length && UNIT_WORDS.contains(tokens[tokenIndex])) {
            tokenIndex++;
        }

        // Skip "of"
        if (tokenIndex < tokens.length && tokens[tokenIndex].equals("of")) {
            tokenIndex++;
        }

        // Also handle cases where quantity is at the end: "Amul milk two packets" or "sugar 2kg"
        StringBuilder remaining = new StringBuilder();
        for (int i = tokenIndex; i < tokens.length; i++) {
            if (i == tokens.length - 2 && parseNumberToken(tokens[i]) != null && UNIT_WORDS.contains(tokens[i + 1])) {
                quantity = parseNumberToken(tokens[i]);
                break;
            } else if (i == tokens.length - 1 && parseNumberToken(tokens[i]) != null) {
                quantity = parseNumberToken(tokens[i]);
                break;
            }
            if (UNIT_WORDS.contains(tokens[i])) {
                continue; // skip dangling unit words like "packets" in "three maggi packets"
            }
            if (!remaining.isEmpty()) remaining.append(" ");
            remaining.append(tokens[i]);
        }

        String product = cleanProductTokens(remaining.toString());
        return new ParsedQuantityAndProduct(quantity, product);
    }

    private Integer parseNumberToken(String token) {
        if (token == null || token.isEmpty()) return null;
        if (token.matches("\\d+")) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ignored) {}
        }
        return NUMBER_WORDS.get(token.toLowerCase(Locale.ROOT));
    }

    private int parseQuantityToken(String token, int fallback) {
        Integer val = parseNumberToken(token);
        return (val != null && val > 0) ? val : fallback;
    }

    public String cleanProductTokens(String text) {
        if (text == null) return "";
        String[] tokens = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String t : tokens) {
            if (t.isBlank()) continue;
            if (STOP_WORDS.contains(t)) continue;
            if (UNIT_WORDS.contains(t)) continue;
            kept.add(t);
        }
        return String.join(" ", kept);
    }

    public List<BillingDataController.ProductView> findMatchingProducts(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String cleanQuery = cleanProductTokens(query);
        List<BillingDataController.ProductView> allProducts = jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE active = TRUE
                """, (rs, row) -> new BillingDataController.ProductView(
                rs.getLong("id"), rs.getString("name"), rs.getString("category"), rs.getString("brand"),
                rs.getString("barcode"), rs.getBigDecimal("purchase_price"), rs.getBigDecimal("price"),
                rs.getInt("stock"), rs.getInt("min_stock"),
                rs.getInt("stock") == 0 ? "Out of Stock" : (rs.getInt("stock") <= rs.getInt("min_stock") ? "Low Stock" : "In Stock"),
                rs.getString("image_path") == null
                        ? ProductImageLibrary.match(rs.getString("name"), rs.getString("category"))
                        : rs.getString("image_path")
        ));

        List<ScoredProduct> scored = new ArrayList<>();
        String[] queryWords = cleanQuery.split("\\s+");

        for (BillingDataController.ProductView p : allProducts) {
            int score = calculateMatchScore(p, cleanQuery, queryWords);
            if (score > 15) {
                scored.add(new ScoredProduct(p, score));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredProduct::score).reversed());

        List<BillingDataController.ProductView> results = new ArrayList<>();
        for (ScoredProduct sp : scored) {
            results.add(sp.product);
        }
        return results;
    }

    private record ScoredProduct(BillingDataController.ProductView product, int score) {}

    private int calculateMatchScore(BillingDataController.ProductView p, String fullQuery, String[] queryWords) {
        String pName = p.name().toLowerCase(Locale.ROOT);
        String pBrand = p.brand().toLowerCase(Locale.ROOT);
        String pCat = p.category().toLowerCase(Locale.ROOT);
        String pBarcode = p.barcode();

        // Barcode exact match
        if (pBarcode.equalsIgnoreCase(fullQuery)) {
            return 100;
        }

        // Exact name match
        if (pName.equalsIgnoreCase(fullQuery)) {
            return 95;
        }

        int score = 0;

        // Substring matches
        if (pName.contains(fullQuery)) {
            score += 50;
        }

        // Check each query word
        int matchedWords = 0;
        for (String word : queryWords) {
            if (word.length() < 2) continue;
            boolean matched = false;

            if (pName.contains(word)) {
                score += 25;
                matched = true;
            } else if (pBrand.contains(word)) {
                score += 20;
                matched = true;
            } else if (pCat.contains(word)) {
                score += 10;
                matched = true;
            } else {
                // Check common aliases
                if (matchesAlias(word, pName)) {
                    score += 25;
                    matched = true;
                }
            }

            if (matched) {
                matchedWords++;
            }
        }

        if (queryWords.length > 0 && matchedWords == queryWords.length) {
            score += 20; // Full query token coverage bonus
        }

        return score;
    }

    private boolean matchesAlias(String queryToken, String productName) {
        // e.g. "milk" -> "Amul Taaza Milk 1L"
        // "maggi" -> "Maggi 2-Min Noodles 4-Pack"
        // "atta" -> "Aashirvaad Whole Wheat Atta 10kg"
        // "bread" -> "Harvest Gold Wheat Bread"
        // "curd" -> "Mother Dairy Curd 400g"
        // "oil" -> "Fortune Sunlite Sunflower Oil 1L"
        // "tea" -> "Red Label Tea 500g"
        // "sugar" -> "Sugar"
        return switch (queryToken) {
            case "milk" -> productName.contains("milk");
            case "maggi" -> productName.contains("maggi") || productName.contains("noodles");
            case "noodle", "noodles" -> productName.contains("noodles") || productName.contains("maggi");
            case "atta", "wheat" -> productName.contains("atta") || productName.contains("wheat");
            case "butter" -> productName.contains("butter");
            case "bread" -> productName.contains("bread");
            case "rice" -> productName.contains("rice");
            case "apple", "apples" -> productName.contains("apple") || productName.contains("apples");
            case "tomato", "tomatoes" -> productName.contains("tomato") || productName.contains("tomatoes");
            case "salt" -> productName.contains("salt");
            case "oil" -> productName.contains("oil");
            case "tea" -> productName.contains("tea");
            case "curd" -> productName.contains("curd");
            case "sugar" -> productName.contains("sugar");
            case "soap" -> productName.contains("soap");
            case "biscuit", "biscuits" -> productName.contains("biscuit") || productName.contains("parle");
            default -> false;
        };
    }

    private boolean isAmbiguous(List<BillingDataController.ProductView> matches, String query) {
        if (matches.size() < 2) return false;
        BillingDataController.ProductView first = matches.get(0);
        BillingDataController.ProductView second = matches.get(1);

        String clean = cleanProductTokens(query);
        // If the top match clearly has both brand and item (e.g. "amul butter" matching "Amul Butter 100g")
        if (first.name().toLowerCase(Locale.ROOT).contains(clean)) {
            return false;
        }

        // If user just said "milk" and there are multiple milk products
        if (clean.equals("milk") || clean.equals("oil") || clean.equals("rice") || clean.equals("tea")) {
            return true;
        }

        return false;
    }
}
