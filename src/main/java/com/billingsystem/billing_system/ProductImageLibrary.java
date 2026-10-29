package com.billingsystem.billing_system;

import java.text.Normalizer;
import java.util.Locale;

final class ProductImageLibrary {
    private ProductImageLibrary() {}

    static String match(String name, String category) {
        String normalizedName = normalize(name);
        String normalizedCategory = normalize(category);
        if (containsAny(normalizedName, "rice")) return image("rice");
        if (containsAny(normalizedName, "chocolate", "cocoa", "cadbury")) return image("chocolate");
        if (containsAny(normalizedName, "milk", "curd", "yogurt", "butter", "cheese", "paneer")) return image("dairy");
        if (containsAny(normalizedName, "sugar")) return image("sugar");
        if (containsAny(normalizedName, "salt")) return image("salt");
        if (containsAny(normalizedName, "flour", "atta")) return image("flour");
        if (containsAny(normalizedName, "oil", "ghee")) return image("oil");
        if (containsAny(normalizedName, "egg", "eggs")) return image("eggs");
        if (containsAny(normalizedName, "bread", "loaf")) return image("bread");
        if (containsAny(normalizedName, "noodle", "noodles", "pasta")) return image("noodles");
        if (containsAny(normalizedName, "tea")) return image("tea");
        if (containsAny(normalizedName, "masala", "spice", "spices")) return image("spices");
        if (containsAny(normalizedName, "ketchup", "sauce")) return image("condiments");
        if (containsAny(normalizedName, "bhujia", "sev", "snack", "snacks", "chips")) return image("snacks");
        if (containsAny(normalizedName, "juice", "tropicana")) return image("juice");
        if (containsAny(normalizedName, "cola", "soda")) return image("soft-drink");
        if (containsAny(normalizedName, "apple", "apples")) return image("apples");
        if (containsAny(normalizedName, "banana", "bananas")) return image("bananas");
        if (containsAny(normalizedName, "tomato", "tomatoes")) return image("tomatoes");
        if (containsAny(normalizedName, "potato", "potatoes")) return image("potatoes");
        if (containsAny(normalizedName, "biscuit", "biscuits", "cookie", "cookies")) return image("biscuits");
        if (containsAny(normalizedName, "soap")) return image("soap");
        if (containsAny(normalizedName, "wheat") && containsAny(normalizedCategory, "grocery")) return image("flour");

        if (containsAny(normalizedCategory, "dairy")) return image("dairy");
        if (containsAny(normalizedCategory, "produce", "fruit", "vegetable")) return image("produce");
        if (containsAny(normalizedCategory, "bakery")) return image("bakery");
        if (containsAny(normalizedCategory, "beverage", "drink")) return image("beverages");
        if (containsAny(normalizedCategory, "personal care")) return image("personal-care");
        if (containsAny(normalizedCategory, "household")) return image("household");
        if (containsAny(normalizedCategory, "grocery")) return image("grocery");
        return image("placeholder");
    }

    static String image(String name) {
        return "/images/products/" + name + ".svg";
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if ((" " + text + " ").contains(" " + term + " ")) return true;
        }
        return false;
    }
}
