package com.tkisor.nekojs.probe.types;

/** Keeps postfix arrays readable without changing the precedence of compound or opaque input types. */
public final class TypeScriptTypeExpressions {
    private TypeScriptTypeExpressions() {}

    public static String arrayOf(String element) {
        return (isPrimary(element) || isGrouped(element) ? element : "(" + element + ")") + "[]";
    }

    private static boolean isPrimary(String type) {
        if (type.isEmpty() || type.indexOf('`') >= 0) return false;
        int index = 0;
        do {
            if (index >= type.length() || !Character.isJavaIdentifierStart(type.charAt(index))) return false;
            while (++index < type.length() && Character.isJavaIdentifierPart(type.charAt(index))) { }
            if (index >= type.length() || type.charAt(index) != '.') break;
            index++;
        } while (true);
        if (index < type.length() && type.charAt(index) == '<') {
            int depth = 0;
            char quote = 0;
            for (; index < type.length(); index++) {
                char current = type.charAt(index);
                if (quote != 0) {
                    if (current == '\\') index++;
                    else if (current == quote) quote = 0;
                } else if (current == '\'' || current == '"') quote = current;
                else if (current == '<') depth++;
                else if (current == '>' && (index == 0 || type.charAt(index - 1) != '=')) {
                    if (--depth == 0) { index++; break; }
                }
            }
            if (depth != 0 || quote != 0) return false;
        }
        while (index + 1 < type.length() && type.charAt(index) == '[' && type.charAt(index + 1) == ']') index += 2;
        return index == type.length();
    }

    private static boolean isGrouped(String type) {
        // Template literal types may contain nested interpolation; opaque expressions keep conservative grouping.
        if (type.length() < 2 || type.charAt(0) != '(' || type.indexOf('`') >= 0) return false;
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < type.length(); index++) {
            char current = type.charAt(index);
            if (quote != 0) {
                if (current == '\\') index++;
                else if (current == quote) quote = 0;
            } else if (current == '\'' || current == '"') quote = current;
            else if (current == '(') depth++;
            else if (current == ')' && --depth == 0) return index == type.length() - 1;
        }
        return false;
    }
}
