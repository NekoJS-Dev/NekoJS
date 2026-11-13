package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Platform-independent JSON value model, a sealed interface used to pass JSON data between
 * scripts and Java.
 *
 * <p>It has six variants: null, boolean, number, string, array and object. A number is stored
 * as its string lexeme, which keeps the original precision and representation losslessly
 * (see {@link NumberValue}). All container variants are immutable.
 *
 * <p>The constants {@link #MAX_DEPTH}/{@link #MAX_NODES}/{@link #MAX_INPUT_CHARS}/
 * {@link #MAX_STRING_CHARS}/{@link #MAX_OUTPUT_CHARS} set the resource limits used when
 * parsing and serializing.
 */
@ContractReceiver
public sealed interface JsonValue permits
        JsonValue.NullValue,
        JsonValue.BooleanValue,
        JsonValue.NumberValue,
        JsonValue.StringValue,
        JsonValue.ArrayValue,
        JsonValue.ObjectValue {

    /** Maximum nesting depth. */
    int MAX_DEPTH = 64;
    /** Maximum total number of nodes. */
    int MAX_NODES = 10_000;
    /** Maximum number of characters in the input string. */
    int MAX_INPUT_CHARS = 1_048_576;
    /** Maximum number of characters in a single string value. */
    int MAX_STRING_CHARS = 1_048_576;
    /** Maximum number of characters in the output string. */
    int MAX_OUTPUT_CHARS = 1_048_576;

    /** Grammar of a JSON number lexeme; covers negative numbers, fractions and exponents. */
    Pattern NUMBER_LEXEME = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /** Returns the null value singleton. */
    static NullValue nullValue() {
        return NullValue.INSTANCE;
    }

    /** Creates a boolean value. */
    static BooleanValue bool(boolean value) {
        return new BooleanValue(value);
    }

    /** Creates a number value from a lexeme that must match {@link #NUMBER_LEXEME}. */
    static NumberValue number(String lexeme) {
        return new NumberValue(lexeme);
    }

    /** Creates a string value; checks surrogate pairing and the length limit. */
    static StringValue string(String value) {
        return new StringValue(value);
    }

    /** Creates an array value; elements must be non-null, and the list is copied into an immutable list. */
    static ArrayValue array(List<JsonValue> values) {
        return new ArrayValue(values);
    }

    /** Creates an object value; checks keys and values and copies them into an immutable map. */
    static ObjectValue object(Map<String, JsonValue> values) {
        return new ObjectValue(values);
    }

    /** The null value, a singleton enum. */
    enum NullValue implements JsonValue {
        INSTANCE
    }

    /** A boolean value. */
    record BooleanValue(boolean value) implements JsonValue {
    }

    /** A number value, stored as a lexeme that must match {@link #NUMBER_LEXEME}. */
    record NumberValue(String lexeme) implements JsonValue {
        public NumberValue {
            Objects.requireNonNull(lexeme, "lexeme");
            if (!NUMBER_LEXEME.matcher(lexeme).matches()) {
                throw new IllegalArgumentException("invalid JSON number lexeme: " + lexeme);
            }
        }
    }

    /** A string value; checks surrogate pairing and the length limit. */
    record StringValue(String value) implements JsonValue {
        public StringValue {
            validateString(value, "string value");
        }
    }

    /** An array value; immutable, and elements must be non-null. */
    record ArrayValue(List<JsonValue> values) implements JsonValue {
        public ArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
            values.forEach(value -> Objects.requireNonNull(value, "array value"));
        }
    }

    /** An object value; immutable, with keys and values checked. */
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
