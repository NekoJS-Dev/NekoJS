package com.tkisor.nekojs.bindings.static_access;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StringUtilsJS {

    /** 驼峰边界（小写/数字 → 大写）：snakeCase 每次调用都用到，预编译避免重复编译正则。 */
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

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
        return CAMEL_BOUNDARY.matcher(value).replaceAll("$1_$2")
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
