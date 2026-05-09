package com.tkisor.nekojs.bindings.static_access;

import java.util.Locale;

public final class StringUtilsJS {
    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    public String decapitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    public String snakeCase(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toLowerCase(Locale.ROOT);
    }

    public String camelCase(String value) {
        if (value == null || value.isEmpty()) return value;

        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '-' || Character.isWhitespace(c)) {
                upperNext = builder.length() > 0;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(builder.isEmpty() ? Character.toLowerCase(c) : c);
            }
        }
        return builder.toString();
    }
}
