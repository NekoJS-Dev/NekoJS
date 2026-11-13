package com.tkisor.nekojs.core.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable SNBT parser, the inverse of {@link NbtSnbtSerializer}.
 *
 * <p>Parses Mojang's string NBT format (compatible with {@code TagParser}):
 * <pre>
 *   {key:value, "quoted key":1b}
 *   [1, 2, 3]            // list
 *   [B;1B,2B,3B]         // byte array
 *   [I;1,2,3]            // int array
 *   123 / 123l / 1.5f / 1.5d / true / false
 *   "quoted string" / unquoted
 * </pre>
 *
 * <p>Enforces the same depth, node and string-length limits as {@link NbtValue}.
 * Exceeding a limit or a syntax error throws {@link IllegalArgumentException}, and the
 * facade turns that into an API error.
 */
public final class NbtSnbtParser {
    private NbtSnbtParser() {
    }

    private static final char EOF = '\0';

    private String input;
    private int position;
    private int nodes;

    public static NbtValue parse(String snbt) {
        if (snbt == null) throw new IllegalArgumentException("SNBT input cannot be null");
        NbtSnbtParser parser = new NbtSnbtParser();
        parser.input = snbt;
        parser.position = 0;
        parser.nodes = 0;
        parser.skipWhitespace();
        NbtValue value = parser.parseValue(0);
        parser.skipWhitespace();
        if (parser.peek() != EOF) {
            throw parser.error("trailing characters after SNBT value");
        }
        return value;
    }

    private NbtValue parseValue(int depth) {
        if (depth > NbtValue.MAX_DEPTH) throw error("NBT nesting exceeds " + NbtValue.MAX_DEPTH);
        if (++nodes > NbtValue.MAX_NODES) throw error("NBT contains more than " + NbtValue.MAX_NODES + " values");
        char current = peek();
        if (current == '{') return parseCompound(depth);
        if (current == '[') return listOrArray(depth);
        if (current == '"' || current == '\'') return NbtValue.string(readQuoted(current));
        return parsePrimitiveOrString();
    }

    private NbtValue parseCompound(int depth) {
        expect('{');
        Map<String, NbtValue> entries = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            advance();
            return NbtValue.compound(entries);
        }
        while (true) {
            skipWhitespace();
            String key = readKey();
            if (key.isEmpty()) throw error("expected compound key");
            skipWhitespace();
            expect(':');
            skipWhitespace();
            NbtValue value = parseValue(depth + 1);
            entries.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                advance();
                continue;
            }
            if (next == '}') {
                advance();
                return NbtValue.compound(entries);
            }
            throw error("expected ',' or '}' in compound");
        }
    }

    private NbtValue listOrArray(int depth) {
        expect('[');
        // Array prefix: [B; ...] / [I; ...] / [L; ...] (L would become a long array, but NbtValue has no long array type, so it is an error)
        if (peekNextNonWhitespace() == ';') {
            char prefix = Character.toUpperCase(peek());
            if (prefix == 'B' || prefix == 'I') {
                advance(); // the prefix letter
                skipWhitespace();
                expect(';');
                return prefix == 'B' ? parseByteArray() : parseIntArray();
            }
        }
        return parseList(depth);
    }

    private NbtValue parseList(int depth) {
        List<NbtValue> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            advance();
            return NbtValue.list(elements);
        }
        while (true) {
            NbtValue element = parseValue(depth + 1);
            elements.add(element);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                advance();
                skipWhitespace();
                continue;
            }
            if (next == ']') {
                advance();
                return NbtValue.list(elements);
            }
            throw error("expected ',' or ']' in list");
        }
    }

    private NbtValue parseByteArray() {
        List<Byte> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            advance();
            return NbtValue.byteArray(toByteArray(elements));
        }
        while (true) {
            NbtValue element = parseValue(0);
            byte value = (byte) coerceIntegral(element, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte array");
            elements.add(value);
            consumeOptionalSuffix(); // allow the trailing B of 1B
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                advance();
                skipWhitespace();
                continue;
            }
            if (next == ']') {
                advance();
                return NbtValue.byteArray(toByteArray(elements));
            }
            throw error("expected ',' or ']' in byte array");
        }
    }

    private NbtValue parseIntArray() {
        List<Integer> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            advance();
            return NbtValue.intArray(toIntArray(elements));
        }
        while (true) {
            NbtValue element = parseValue(0);
            int value = (int) coerceIntegral(element, Integer.MIN_VALUE, Integer.MAX_VALUE, "int array");
            elements.add(value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                advance();
                skipWhitespace();
                continue;
            }
            if (next == ']') {
                advance();
                return NbtValue.intArray(toIntArray(elements));
            }
            throw error("expected ',' or ']' in int array");
        }
    }

    /** Parses a bare scalar (a number with a suffix / true / false) or an unquoted string. */
    private NbtValue parsePrimitiveOrString() {
        int start = position;
        char current = peek();
        while (current != EOF && current != ',' && current != '}' && current != ']'
                && current != ':' && !Character.isWhitespace(current)) {
            advance();
            current = peek();
        }
        if (start == position) throw error("unexpected end of SNBT");
        String token = input.substring(start, position);
        return interpretToken(token);
    }

    private NbtValue interpretToken(String token) {
        if (token.isEmpty()) throw error("empty token");
        if (token.equalsIgnoreCase("true")) return NbtValue.byteValue((byte) 1);
        if (token.equalsIgnoreCase("false")) return NbtValue.byteValue((byte) 0);
        // A number with a suffix
        char last = token.charAt(token.length() - 1);
        if (isNumber(token)) {
            return parsePlainNumber(token);
        }
        if (token.length() >= 2) {
            char suffix = Character.toLowerCase(last);
            String body = token.substring(0, token.length() - 1);
            try {
                switch (suffix) {
                    case 'b': return NbtValue.byteValue((byte) Long.parseLong(body));
                    case 's': return NbtValue.shortValue((short) Long.parseLong(body));
                    case 'l': return NbtValue.longValue(Long.parseLong(body));
                    case 'f': return NbtValue.floatValue(Float.parseFloat(body));
                    case 'd': return NbtValue.doubleValue(Double.parseDouble(body));
                    default: // no suffix: a value that parses as a number is a literal, anything else stays a string
                        break;
                }
            } catch (NumberFormatException ignored) {
                // suffix present but the value does not parse -> treat the token as a plain string
            }
        }
        return NbtValue.string(token);
    }

    private static NbtValue parsePlainNumber(String token) {
        try {
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                return NbtValue.doubleValue(Double.parseDouble(token));
            }
            long parsed = Long.parseLong(token);
            // Integer without a suffix: int when it fits the int range (matching the vanilla TagParser), long otherwise
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                return NbtValue.intValue((int) parsed);
            }
            return NbtValue.longValue(parsed);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid number token: " + token);
        }
    }

    private static boolean isNumber(String token) {
        if (token.isEmpty()) return false;
        int i = 0;
        if (token.charAt(0) == '+' || token.charAt(0) == '-') i = 1;
        if (i >= token.length()) return false;
        boolean hasDigit = false;
        boolean hasDot = false;
        for (; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isDigit(c)) { hasDigit = true; continue; }
            if (c == '.' && !hasDot) { hasDot = true; continue; }
            if ((c == 'e' || c == 'E') && hasDigit) continue;
            if ((c == '+' || c == '-') && i > 0) {
                char prev = token.charAt(i - 1);
                if (prev == 'e' || prev == 'E') continue;
            }
            return false;
        }
        return hasDigit;
    }

    private String readKey() {
        char current = peek();
        if (current == '"' || current == '\'') return readQuoted(current);
        int start = position;
        while (current != EOF && current != ':' && current != ',' && current != '}'
                && !Character.isWhitespace(current)) {
            advance();
            current = peek();
        }
        return input.substring(start, position);
    }

    private String readQuoted(char quote) {
        advance(); // the opening quote
        StringBuilder builder = new StringBuilder();
        while (true) {
            char current = peek();
            if (current == EOF) throw error("unterminated string");
            if (current == '\\') {
                advance();
                char escaped = peek();
                if (escaped == EOF) throw error("unterminated escape");
                builder.append(unescape(escaped));
                advance();
                continue;
            }
            if (current == quote) {
                advance();
                return builder.toString();
            }
            builder.append(current);
            advance();
        }
    }

    private static char unescape(char escaped) {
        return switch (escaped) {
            case '"' -> '"';
            case '\'' -> '\'';
            case '\\' -> '\\';
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '/' -> '/';
            case '0' -> '\0';
            default -> escaped;
        };
    }

    private void consumeOptionalSuffix() {
        char current = peek();
        if (current == 'b' || current == 'B' || current == 's' || current == 'S') {
            advance();
        }
    }

    private char peek() {
        return position < input.length() ? input.charAt(position) : EOF;
    }

    private char peekNextNonWhitespace() {
        for (int i = position + 1; i < input.length(); i++) {
            if (!Character.isWhitespace(input.charAt(i))) return input.charAt(i);
        }
        return EOF;
    }

    private void advance() {
        if (position < input.length()) position++;
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
    }

    private void expect(char expected) {
        if (peek() != expected) throw error("expected '" + expected + "'");
        advance();
    }

    private static long coerceIntegral(NbtValue value, long min, long max, String label) {
        long number;
        if (value instanceof NbtValue.ByteValue b) number = b.value();
        else if (value instanceof NbtValue.ShortValue s) number = s.value();
        else if (value instanceof NbtValue.IntValue i) number = i.value();
        else if (value instanceof NbtValue.LongValue l) number = l.value();
        else throw new IllegalArgumentException(label + " element must be an integer");
        if (number < min || number > max) throw new IllegalArgumentException(label + " element out of range: " + number);
        return number;
    }

    private static byte[] toByteArray(List<Byte> values) {
        byte[] result = new byte[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position);
    }
}
