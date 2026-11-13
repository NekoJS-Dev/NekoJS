package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String helpers: blank checks and conversion between naming conventions such as camelCase and
 * snake_case.
 */
@Doc("String helpers for blank checks and case-convention conversion.")
public final class StringUtilsJS {

    /** A camel-case boundary (lowercase or digit followed by uppercase). snakeCase uses it on every
     * call, so the pattern is compiled once instead of on each call. */
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    /** Whether the value is null, empty, or whitespace only. */
    @Doc("Checks whether the value is null, empty, or whitespace-only.")
    @Param(name = "value", value = "string to check; null is allowed")
    @Return("true when null, empty, or whitespace-only")
    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Whether the value is null or the empty string (whitespace alone counts as non-empty). */
    @Doc("Checks whether the value is null or the empty string (whitespace counts as non-empty).")
    @Param(name = "value", value = "string to check; null is allowed")
    @Return("true only when null or empty")
    public boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** Converts the first character to upper case. */
    @Doc("Upper-cases the first character, keeping the rest unchanged.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("capitalized string, or the input unchanged when null/empty")
    public String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /** Converts the first character to lower case. */
    @Doc("Lower-cases the first character, keeping the rest unchanged.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("decapitalized string, or the input unchanged when null/empty")
    public String decapitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    /** Converts to lower snake_case; camelCase, kebab-case and space-separated input are all split
     * at their separators. */
    @Doc("Converts camelCase, kebab-case, or space-separated input to lower_snake_case.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("snake_case string, or the input unchanged when null/empty")
    public String snakeCase(String value) {
        if (value == null || value.isEmpty()) return value;
        return CAMEL_BOUNDARY.matcher(value).replaceAll("$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toLowerCase(Locale.ROOT);
    }

    /** Converts to lower camelCase: the first character is lower case and the character after each
     * separator becomes upper case. */
    @Doc("Converts snake_case, kebab-case, or space-separated input to camelCase.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("camelCase string, or the input unchanged when null/empty")
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
