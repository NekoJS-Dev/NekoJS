package com.tkisor.nekojs.core.api.json;

import com.tkisor.nekojs.api.data.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValueParser {
    private final String source;
    private int offset;
    private int nodes;

    private JsonValueParser(String source) {
        this.source = source;
    }

    public static JsonValue parse(String source) {
        if (source == null) {
            throw JsonValueException.invalid("JSON source cannot be null");
        }
        if (source.length() > JsonValue.MAX_INPUT_CHARS) {
            throw JsonValueException.limit("JSON input exceeds " + JsonValue.MAX_INPUT_CHARS + " characters");
        }
        JsonValueParser parser = new JsonValueParser(source);
        parser.skipWhitespace();
        JsonValue value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.invalid("Unexpected trailing content");
        }
        return value;
    }

    private JsonValue parseValue(int depth) {
        if (depth > JsonValue.MAX_DEPTH) {
            throw JsonValueException.limit("JSON nesting exceeds " + JsonValue.MAX_DEPTH);
        }
        countNode();
        if (atEnd()) throw invalid("Expected JSON value");
        return switch (source.charAt(offset)) {
            case 'n' -> parseKeyword("null", JsonValue.nullValue());
            case 't' -> parseKeyword("true", JsonValue.bool(true));
            case 'f' -> parseKeyword("false", JsonValue.bool(false));
            case '"' -> JsonValue.string(parseString());
            case '[' -> parseArray(depth + 1);
            case '{' -> parseObject(depth + 1);
            default -> parseNumber();
        };
    }

    private JsonValue parseKeyword(String keyword, JsonValue value) {
        if (!source.startsWith(keyword, offset)) {
            throw invalid("Invalid JSON literal");
        }
        offset += keyword.length();
        return value;
    }

    private JsonValue parseArray(int depth) {
        consume('[');
        skipWhitespace();
        List<JsonValue> values = new ArrayList<>();
        if (consumeIf(']')) return JsonValue.array(values);
        while (true) {
            values.add(parseValue(depth));
            skipWhitespace();
            if (consumeIf(']')) return JsonValue.array(values);
            consume(',');
            skipWhitespace();
        }
    }

    private JsonValue parseObject(int depth) {
        consume('{');
        skipWhitespace();
        Map<String, JsonValue> values = new LinkedHashMap<>();
        if (consumeIf('}')) return JsonValue.object(values);
        while (true) {
            if (atEnd() || source.charAt(offset) != '"') {
                throw invalid("Expected object key");
            }
            String key = parseString();
            if (values.containsKey(key)) {
                throw invalid("Duplicate object key '" + key + "'");
            }
            skipWhitespace();
            consume(':');
            skipWhitespace();
            values.put(key, parseValue(depth));
            skipWhitespace();
            if (consumeIf('}')) return JsonValue.object(values);
            consume(',');
            skipWhitespace();
        }
    }

    private JsonValue parseNumber() {
        int start = offset;
        consumeIf('-');
        if (consumeIf('0')) {
            if (!atEnd() && Character.isDigit(source.charAt(offset))) {
                throw invalid("Leading zero in JSON number");
            }
        } else {
            consumeDigits("Expected JSON number");
        }
        if (consumeIf('.')) consumeDigits("Expected digits after decimal point");
        if (consumeIf('e') || consumeIf('E')) {
            consumeIf('+');
            consumeIf('-');
            consumeDigits("Expected exponent digits");
        }
        try {
            return JsonValue.number(source.substring(start, offset));
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }

    private void consumeDigits(String message) {
        int start = offset;
        while (!atEnd() && Character.isDigit(source.charAt(offset))) offset++;
        if (offset == start) throw invalid(message);
    }

    private String parseString() {
        consume('"');
        StringBuilder result = new StringBuilder();
        while (!atEnd()) {
            char current = source.charAt(offset++);
            if (current == '"') {
                try {
                    return JsonValue.string(result.toString()).value();
                } catch (IllegalArgumentException e) {
                    throw invalid(e.getMessage());
                }
            }
            if (current < 0x20) throw invalid("Control character in JSON string");
            if (current != '\\') {
                result.append(current);
            } else {
                if (atEnd()) throw invalid("Unterminated JSON escape");
                switch (source.charAt(offset++)) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> appendUnicodeEscape(result);
                    default -> throw invalid("Invalid JSON escape");
                }
            }
            if (result.length() > JsonValue.MAX_STRING_CHARS) {
                throw JsonValueException.limit("JSON string exceeds " + JsonValue.MAX_STRING_CHARS + " characters");
            }
        }
        throw invalid("Unterminated JSON string");
    }

    private void appendUnicodeEscape(StringBuilder result) {
        char first = readHexCharacter();
        if (Character.isHighSurrogate(first)) {
            if (offset + 2 > source.length() || source.charAt(offset) != '\\' || source.charAt(offset + 1) != 'u') {
                throw invalid("Unpaired high surrogate in JSON string");
            }
            offset += 2;
            char second = readHexCharacter();
            if (!Character.isLowSurrogate(second)) throw invalid("Invalid surrogate pair in JSON string");
            result.append(first).append(second);
        } else if (Character.isLowSurrogate(first)) {
            throw invalid("Unpaired low surrogate in JSON string");
        } else {
            result.append(first);
        }
    }

    private char readHexCharacter() {
        if (offset + 4 > source.length()) throw invalid("Incomplete Unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = asciiHexDigit(source.charAt(offset++));
            if (digit < 0) throw invalid("Invalid Unicode escape");
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private static int asciiHexDigit(char value) {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'a' && value <= 'f') return value - 'a' + 10;
        if (value >= 'A' && value <= 'F') return value - 'A' + 10;
        return -1;
    }

    private void countNode() {
        if (++nodes > JsonValue.MAX_NODES) {
            throw JsonValueException.limit("JSON contains more than " + JsonValue.MAX_NODES + " values");
        }
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char current = source.charAt(offset);
            if (current != ' ' && current != '\n' && current != '\r' && current != '\t') return;
            offset++;
        }
    }

    private boolean consumeIf(char expected) {
        if (!atEnd() && source.charAt(offset) == expected) {
            offset++;
            return true;
        }
        return false;
    }

    private void consume(char expected) {
        if (!consumeIf(expected)) throw invalid("Expected '" + expected + "'");
    }

    private boolean atEnd() {
        return offset >= source.length();
    }

    private JsonValueException invalid(String message) {
        return JsonValueException.invalid(message + " at offset " + offset);
    }
}
