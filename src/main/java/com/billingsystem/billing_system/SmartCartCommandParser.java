package com.billingsystem.billing_system;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmartCartCommandParser {
    private static final String NUMBER = "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)";
    private static final Pattern ADD_QUANTITY = Pattern.compile(
            "^" + NUMBER + "(?:\\s+(packets?|packs?|pieces?|items?|kilograms?|kilos?|kgs?|grams?|g|litres?|liters?|l))?(?:\\s+of)?\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_QUANTITY = Pattern.compile(
            "^(?:set|change|update|increase|decrease)\\s+(?:the\\s+)?(.+?)\\s+(?:quantity\\s+)?to\\s+" + NUMBER + "$",
            Pattern.CASE_INSENSITIVE);

    private SmartCartCommandParser() {}

    public static ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) return null;
        String command = input.strip().toLowerCase(Locale.ROOT)
                .replaceAll("^[\\s,;]+|[\\s,;.!?]+$", "")
                .replaceAll("\\s+", " ")
                .replaceFirst("^please\\s+", "");
        if (command.isBlank()) return null;

        if (command.matches("^(show|view|open)( my)? cart$")) {
            return new ParsedCommand("VIEW_CART", null, 0, null);
        }
        if (command.matches("^(what is|what's|tell me) (my )?(cart )?(total|total bill)( please)?$")) {
            return new ParsedCommand("GET_TOTAL", null, 0, null);
        }
        if (command.matches("^(clear|empty)( my)? cart$")) {
            return new ParsedCommand("CLEAR_CART", null, 0, null);
        }

        Matcher search = Pattern.compile("^(?:search(?:\\s+for)?|find)\\s+(.+)$").matcher(command);
        if (search.matches()) return new ParsedCommand("SEARCH_PRODUCT", cleanQuery(search.group(1)), 0, null);

        Matcher update = UPDATE_QUANTITY.matcher(command);
        if (update.matches()) {
            return new ParsedCommand("UPDATE_QUANTITY", cleanQuery(update.group(1)),
                    parseNumber(update.group(2)), null);
        }

        Matcher remove = Pattern.compile("^(?:remove|delete)\\s+(.+?)(?:\\s+from (?:my )?cart)?$")
                .matcher(command);
        if (remove.matches()) return new ParsedCommand("REMOVE_PRODUCT", cleanQuery(remove.group(1)), 0, null);

        Matcher add = Pattern.compile("^(?:add|put|include)\\s+(.+)$").matcher(command);
        if (add.matches()) {
            String value = add.group(1).strip();
            Matcher quantity = ADD_QUANTITY.matcher(value);
            if (quantity.matches()) {
                int count = parseNumber(quantity.group(1));
                String unit = quantity.group(2);
                String query = cleanQuery(quantity.group(3));
                return query.isBlank() ? null : new ParsedCommand("ADD_PRODUCT", query, count, unit);
            }
            return new ParsedCommand("ADD_PRODUCT", cleanQuery(value), 1, null);
        }
        return null;
    }

    private static String cleanQuery(String value) {
        return value.strip()
                .replaceFirst("(?i)\\s+(?:packets?|packs?|pieces?|items?)$", "")
                .replaceAll("\\s+", " ");
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "one" -> 1;
                case "two" -> 2;
                case "three" -> 3;
                case "four" -> 4;
                case "five" -> 5;
                case "six" -> 6;
                case "seven" -> 7;
                case "eight" -> 8;
                case "nine" -> 9;
                case "ten" -> 10;
                default -> throw new IllegalArgumentException("Unsupported quantity: " + value);
            };
        }
    }

    public record ParsedCommand(String intent, String query, int quantity, String unit) {}
}
