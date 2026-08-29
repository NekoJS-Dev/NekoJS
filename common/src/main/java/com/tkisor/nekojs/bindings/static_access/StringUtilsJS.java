package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 字符串工具：判空与命名风格转换（camelCase / snake_case 等）。
 */
@Doc("String helpers for blank checks and case-convention conversion.")
public final class StringUtilsJS {

    /** 驼峰边界（小写/数字 → 大写）：snakeCase 每次调用都用到，预编译避免重复编译正则。 */
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    /** 是否为 null、空串或纯空白。 */
    @Doc("Checks whether the value is null, empty, or whitespace-only.")
    @Param(name = "value", value = "string to check; null is allowed")
    @Return("true when null, empty, or whitespace-only")
    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 是否为 null 或空串（不忽略空白）。 */
    @Doc("Checks whether the value is null or the empty string (whitespace counts as non-empty).")
    @Param(name = "value", value = "string to check; null is allowed")
    @Return("true only when null or empty")
    public boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** 首字母大写。 */
    @Doc("Upper-cases the first character, keeping the rest unchanged.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("capitalized string, or the input unchanged when null/empty")
    public String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /** 首字母小写。 */
    @Doc("Lower-cases the first character, keeping the rest unchanged.")
    @Param(name = "value", value = "input string; null is allowed")
    @Return("decapitalized string, or the input unchanged when null/empty")
    public String decapitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    /** 转为小写下划线形式（camelCase / kebab-case / 空格均按分隔处理）。 */
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

    /** 转为小驼峰形式（首字母小写，后续分隔符后的字母大写）。 */
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
