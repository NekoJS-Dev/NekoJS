package com.tkisor.nekojs.core.module.esm;

import java.util.ArrayList;
import java.util.List;

public final class NekoEsmLexer {
    private final String source;
    private final int length;

    public NekoEsmLexer(String source) {
        this.source = source == null ? "" : source;
        this.length = this.source.length();
    }

    public List<NekoEsmToken> tokenize() {
        List<NekoEsmToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/') {
                if (peek(i + 1) == '/') {
                    i = skipLineComment(i + 2);
                    continue;
                }
                if (peek(i + 1) == '*') {
                    i = skipBlockComment(i + 2);
                    continue;
                }
                if (looksLikeRegexStart(tokens)) {
                    int end = skipRegex(i + 1);
                    tokens.add(token(NekoEsmTokenKind.REGEX, i, end, source.substring(i, end), null));
                    i = end;
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                StringRead string = readString(i, c);
                tokens.add(token(NekoEsmTokenKind.STRING, i, string.end, source.substring(i, string.end), string.value));
                i = string.end;
                continue;
            }
            if (c == '`') {
                int end = skipTemplate(i);
                tokens.add(token(NekoEsmTokenKind.TEMPLATE, i, end, source.substring(i, end), null));
                i = end;
                continue;
            }
            if (isIdentifierStart(c)) {
                int end = readIdentifierEnd(i + 1);
                tokens.add(token(NekoEsmTokenKind.IDENTIFIER, i, end, source.substring(i, end), source.substring(i, end)));
                i = end;
                continue;
            }
            if (source.startsWith("...", i)) {
                tokens.add(token(NekoEsmTokenKind.PUNCTUATOR, i, i + 3, "...", null));
                i += 3;
                continue;
            }
            tokens.add(token(NekoEsmTokenKind.PUNCTUATOR, i, i + 1, String.valueOf(c), null));
            i++;
        }
        tokens.add(new NekoEsmToken(NekoEsmTokenKind.EOF, "<eof>", null, new NekoEsmSpan(length, length)));
        return List.copyOf(tokens);
    }

    private NekoEsmToken token(NekoEsmTokenKind kind, int start, int end, String text, String value) {
        return new NekoEsmToken(kind, text, value, new NekoEsmSpan(start, end));
    }

    private StringRead readString(int start, char quote) {
        StringBuilder value = new StringBuilder();
        int i = start + 1;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '\\') {
                if (i + 1 < length) {
                    value.append(source.charAt(i + 1));
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (c == quote) {
                return new StringRead(i + 1, value.toString());
            }
            value.append(c);
            i++;
        }
        return new StringRead(length, value.toString());
    }

    private int skipTemplate(int start) {
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

    private int skipRegex(int start) {
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
                while (i < length && Character.isLetter(source.charAt(i))) i++;
                return i;
            }
            i++;
        }
        return length;
    }

    private boolean looksLikeRegexStart(List<NekoEsmToken> tokens) {
        if (tokens.isEmpty()) return true;
        NekoEsmToken previous = tokens.get(tokens.size() - 1);
        if (previous.kind() == NekoEsmTokenKind.IDENTIFIER || previous.kind() == NekoEsmTokenKind.STRING || previous.kind() == NekoEsmTokenKind.TEMPLATE || previous.kind() == NekoEsmTokenKind.REGEX) {
            return false;
        }
        String text = previous.text();
        return "(".equals(text) || "{".equals(text) || "[".equals(text) || "=".equals(text) || ":".equals(text) || ",".equals(text) || ";".equals(text) || "!".equals(text) || "&".equals(text) || "|".equals(text) || "?".equals(text) || "+".equals(text) || "-".equals(text) || "*".equals(text) || "~".equals(text) || "^".equals(text) || "<".equals(text) || ">".equals(text);
    }

    private int skipLineComment(int start) {
        int i = start;
        while (i < length && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
        return i;
    }

    private int skipBlockComment(int start) {
        int i = start;
        while (i + 1 < length) {
            if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') return i + 2;
            i++;
        }
        return length;
    }

    private char peek(int index) {
        return index >= 0 && index < length ? source.charAt(index) : '\0';
    }

    private int readIdentifierEnd(int start) {
        int i = start;
        while (i < length && isIdentifierPart(source.charAt(i))) i++;
        return i;
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private record StringRead(int end, String value) {}
}
