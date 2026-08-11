package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.regex.Pattern;

@ContractReceiver
public sealed interface JsonValue permits
        JsonValue.NullValue,
        JsonValue.BooleanValue,
        JsonValue.NumberValue,
        JsonValue.StringValue,
        JsonValue.ArrayValue,
        JsonValue.ObjectValue {

    int MAX_DEPTH = 64;
    int MAX_NODES = 10_000;
    int MAX_INPUT_CHARS = 1_048_576;
    int MAX_STRING_CHARS = 1_048_576;
    int MAX_OUTPUT_CHARS = 1_048_576;

    Pattern NUMBER_LEXEME = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    static NullValue nullValue() {
        return NullValue.INSTANCE;
    }

    static BooleanValue bool(boolean value) {
        return new BooleanValue(value);
    }

    static NumberValue number(String lexeme) {
        return new NumberValue(lexeme);
    }

    static StringValue string(String value) {
        return new StringValue(value);
    }

    static ArrayValue array(List<JsonValue> values) {
        return new ArrayValue(values);
    }

    static ObjectValue object(Map<String, JsonValue> values) {
        return new ObjectValue(values);
    }

    enum NullValue implements JsonValue {
        INSTANCE
    }

    record BooleanValue(boolean value) implements JsonValue {
    }

    record NumberValue(String lexeme) implements JsonValue {
        public NumberValue {
            Objects.requireNonNull(lexeme, "lexeme");
            if (!NUMBER_LEXEME.matcher(lexeme).matches()) {
                throw new IllegalArgumentException("invalid JSON number lexeme: " + lexeme);
            }
        }
    }

    record StringValue(String value) implements JsonValue {
        public StringValue {
            validateString(value, "string value");
        }
    }

    record ArrayValue(List<JsonValue> values) implements JsonValue {
        public ArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
            values.forEach(value -> Objects.requireNonNull(value, "array value"));
        }
    }

    record ObjectValue(Map<String, JsonValue> values) implements JsonValue {
        public ObjectValue {
            Objects.requireNonNull(values, "values");
            Map<String, JsonValue> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                validateString(key, "object key");
                copy.put(key, Objects.requireNonNull(value, "object value"));
            });
            values = Collections.unmodifiableMap(copy);
        }
    }

    private static void validateString(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > MAX_STRING_CHARS) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_STRING_CHARS + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired high surrogate");
                }
                i++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired low surrogate");
            }
        }
    }
}
