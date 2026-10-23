package io.github.alex59.relationalpaths;

import java.util.Objects;
import java.util.regex.Pattern;

final class Names {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private Names() { }
    static String identifier(String value) {
        Objects.requireNonNull(value, "identifier");
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        return value;
    }
    static String qualified(String value) {
        Objects.requireNonNull(value, "table name");
        String[] parts = value.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("Expected table or schema.table: " + value);
        }
        for (String part : parts) identifier(part);
        return value;
    }
    static String quote(String value) { return "\"" + identifier(value) + "\""; }
    static String quoteTable(String value) {
        return java.util.Arrays.stream(value.split("\\.")).map(Names::quote)
                .collect(java.util.stream.Collectors.joining("."));
    }
}
