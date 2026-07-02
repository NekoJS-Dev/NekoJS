package com.tkisor.nekojs.api.event;

import java.util.Set;

/**
 * 共享的脚本源码成员访问扫描器：扫描指定标识符集合的 {@code ident.member} 与
 * {@code ident["member"]} 访问，定位到偏移量后回调。
 *
 * <p>供加载时校验器复用：{@link EventCallbackSourceValidator}（事件回调参数）与
 * {@code GlobalBindingMemberValidator}（全局绑定对象）都通过它捕获成员访问位点，
 * 再各自对照 Java 反射校验成员是否存在。
 *
 * <p>纯词法扫描，不构建 AST：跳过字符串 / 模板 / 注释，识别标识符边界，
 * 对集合内的标识符捕获其后的属性访问。动态计算访问（{@code obj[key]} 中 key 非字符串字面量、
 * 变量中转 {@code const u = Utils; u.foo}）不覆盖 —— 这是静态扫描的固有边界。
 */
public final class ScriptMemberAccessScanner {

    private ScriptMemberAccessScanner() {}

    public interface MemberConsumer {
        void accept(String identifier, String member, int offset);
    }

    /**
     * 扫描 {@code source}，对 {@code identifiers} 中任一标识符的 {@code .member} 或
     * {@code ["member"]} 访问回调 {@code consumer}。
     */
    public static void scan(String source, Set<String> identifiers, MemberConsumer consumer) {
        if (source == null || source.isEmpty() || identifiers == null || identifiers.isEmpty()) {
            return;
        }
        for (int i = 0; i < source.length(); ) {
            char c = source.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(source, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(source, i);
                continue;
            }
            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    i = skipLineComment(source, i + 2);
                    continue;
                }
                if (next == '*') {
                    i = skipBlockComment(source, i + 2);
                    continue;
                }
            }
            if (!isIdentifierStart(c)) {
                i++;
                continue;
            }

            int identStart = i++;
            while (isIdentifierPart(charAt(source, i))) {
                i++;
            }
            String identifier = source.substring(identStart, i);
            if (!identifiers.contains(identifier) || !boundaryBefore(source, identStart) || isAfterDot(source, identStart)) {
                continue;
            }

            int cursor = skipWhitespace(source, i);
            if (source.startsWith("?.", cursor)) {
                cursor += 2;
            } else if (charAt(source, cursor) == '.') {
                cursor++;
            } else if (charAt(source, cursor) == '[') {
                scanBracketMember(source, cursor, identifier, consumer);
                continue;
            } else {
                continue;
            }
            cursor = skipWhitespace(source, cursor);
            if (isIdentifierStart(charAt(source, cursor))) {
                int memberStart = cursor++;
                while (isIdentifierPart(charAt(source, cursor))) {
                    cursor++;
                }
                consumer.accept(identifier, source.substring(memberStart, cursor), memberStart);
            }
        }
    }

    private static void scanBracketMember(String source, int open, String identifier, MemberConsumer consumer) {
        int i = skipWhitespace(source, open + 1);
        char quote = charAt(source, i);
        if (quote != '\'' && quote != '"') {
            return;
        }
        int start = i + 1;
        int end = start;
        while (end < source.length()) {
            char c = source.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == quote) {
                int close = skipWhitespace(source, end + 1);
                if (charAt(source, close) == ']') {
                    consumer.accept(identifier, source.substring(start, end), start);
                }
                return;
            }
            end++;
        }
    }

    private static int skipWhitespace(String source, int index) {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipQuoted(String source, int start, char quote) {
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int skipTemplate(String source, int start) {
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '`') {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int skipLineComment(String source, int start) {
        int end = source.indexOf('\n', start);
        return end < 0 ? source.length() : end + 1;
    }

    private static int skipBlockComment(String source, int start) {
        int end = source.indexOf("*/", start);
        return end < 0 ? source.length() : end + 2;
    }

    private static boolean boundaryBefore(String source, int index) {
        return index <= 0 || !isIdentifierPart(source.charAt(index - 1));
    }

    /**
     * 标识符前（跳过空白）是否是 {@code .} 或 {@code ?.} —— 即它处于属性访问链中
     * （{@code obj.Utils.foo}），而非全局引用，应跳过以避免误报。
     */
    private static boolean isAfterDot(String source, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '.';
        }
        return false;
    }

    private static char charAt(String source, int index) {
        return index >= 0 && index < source.length() ? source.charAt(index) : '\0';
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c);
    }
}
