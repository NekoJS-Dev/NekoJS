package com.tkisor.nekojs.core.compiler;

final class NekoSourceLexerBase {
    private NekoSourceLexerBase() {}

    static int skipString(String source, int length, int start, char quote) {
        int i = start + 1;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            i++;
        }
        return length;
    }

    static int skipTemplate(String source, int length, int start) {
        int i = start + 1;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') return i + 1;
            i++;
        }
        return length;
    }

    static int skipLineComment(String source, int length, int start) {
        int i = start;
        while (i < length && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
        return i;
    }

    static int skipBlockComment(String source, int length, int start) {
        int i = start;
        while (i + 1 < length) {
            if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') return i + 2;
            i++;
        }
        return length;
    }

    static int skipRegex(String source, int length, int start) {
        int i = start;
        boolean inClass = false;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) {
                i++;
                while (i < length && isIdentifierPart(source.charAt(i))) i++;
                return i;
            }
            i++;
        }
        return length;
    }

    static boolean looksLikeRegexStart(String source, int length, int slash) {
        int previous = previousNonWhitespace(source, length, slash - 1);
        if (previous < 0) return true;
        char c = source.charAt(previous);
        return "=(:,[!&|?;{}<>+-*/%\n\r".indexOf(c) >= 0;
    }

    static int previousNonWhitespace(String source, int length, int index) {
        int i = Math.min(index, length - 1);
        while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
        return i;
    }

    static int nextNonWhitespace(String source, int length, int index) {
        int i = Math.max(0, index);
        while (i < length && Character.isWhitespace(source.charAt(i))) i++;
        return i;
    }

    static int readIdentifierEnd(String source, int length, int start) {
        int i = start;
        while (i < length && isIdentifierPart(source.charAt(i))) i++;
        return i;
    }

    static boolean startsWithKeyword(String source, int length, int start, String keyword) {
        if (start < 0 || start + keyword.length() > length || !source.startsWith(keyword, start)) return false;
        boolean before = start == 0 || !isIdentifierPart(source.charAt(start - 1));
        boolean after = start + keyword.length() >= length || !isIdentifierPart(source.charAt(start + keyword.length()));
        return before && after;
    }

    static boolean isIdentifierStart(char c) {
        return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
    }

    static boolean isIdentifierPart(char c) {
        return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
    }

    static String position(String source, int length, int index) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < index && i < length; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return line + ":" + column;
    }

    static int skipSlash(String source, int length, int slash) {
        if (slash + 1 >= length) return slash;
        char next = source.charAt(slash + 1);
        if (next == '/') return skipLineComment(source, length, slash + 2);
        if (next == '*') return skipBlockComment(source, length, slash + 2);
        if (looksLikeRegexStart(source, length, slash)) return skipRegex(source, length, slash + 1);
        return slash;
    }
}
