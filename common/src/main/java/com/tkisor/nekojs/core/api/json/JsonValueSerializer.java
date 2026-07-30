package com.tkisor.nekojs.core.api.json;

import com.tkisor.nekojs.api.data.JsonValue;

import java.util.Map;

public final class JsonValueSerializer {
    private JsonValueSerializer() {
    }

    public static String compact(JsonValue value) {
        return write(value, false);
    }

    public static String pretty(JsonValue value) {
        return write(value, true);
    }

    private static String write(JsonValue value, boolean pretty) {
        if (value == null) throw JsonValueException.invalid("JSON value cannot be null");
        Output output = new Output();
        writeValue(value, output, pretty, 0, new Counter());
        return output.toString();
    }

    private static void writeValue(JsonValue value, Output output, boolean pretty, int depth, Counter counter) {
        if (depth > JsonValue.MAX_DEPTH) throw JsonValueException.limit("JSON nesting exceeds " + JsonValue.MAX_DEPTH);
        counter.increment();
        if (value instanceof JsonValue.NullValue) {
            output.append("null");
        } else if (value instanceof JsonValue.BooleanValue bool) {
            output.append(bool.value() ? "true" : "false");
        } else if (value instanceof JsonValue.NumberValue number) {
            output.append(number.lexeme());
        } else if (value instanceof JsonValue.StringValue string) {
            writeString(string.value(), output);
        } else if (value instanceof JsonValue.ArrayValue array) {
            writeArray(array, output, pretty, depth, counter);
        } else {
            writeObject((JsonValue.ObjectValue) value, output, pretty, depth, counter);
        }
    }

    private static void writeArray(
            JsonValue.ArrayValue array,
            Output output,
            boolean pretty,
            int depth,
            Counter counter) {
        output.append('[');
        if (!array.values().isEmpty()) {
            for (int i = 0; i < array.values().size(); i++) {
                if (i > 0) output.append(',');
                if (pretty) newline(output, depth + 1);
                writeValue(array.values().get(i), output, pretty, depth + 1, counter);
            }
            if (pretty) newline(output, depth);
        }
        output.append(']');
    }

    private static void writeObject(
            JsonValue.ObjectValue object,
            Output output,
            boolean pretty,
            int depth,
            Counter counter) {
        output.append('{');
        int index = 0;
        for (Map.Entry<String, JsonValue> entry : object.values().entrySet()) {
            if (index++ > 0) output.append(',');
            if (pretty) newline(output, depth + 1);
            writeString(entry.getKey(), output);
            output.append(pretty ? ": " : ":");
            writeValue(entry.getValue(), output, pretty, depth + 1, counter);
        }
        if (pretty && !object.values().isEmpty()) newline(output, depth);
        output.append('}');
    }

    private static void writeString(String value, Output output) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20) {
                        output.append(String.format("\\u%04x", (int) current));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }

    private static void newline(Output output, int indent) {
        output.append('\n');
        for (int i = 0; i < indent; i++) output.append("  ");
    }

    private static final class Counter {
        private int nodes;

        void increment() {
            if (++nodes > JsonValue.MAX_NODES) {
                throw JsonValueException.limit("JSON contains more than " + JsonValue.MAX_NODES + " values");
            }
        }
    }

    private static final class Output {
        private final StringBuilder builder = new StringBuilder();

        void append(char value) {
            ensureCapacity(1);
            builder.append(value);
        }

        void append(String value) {
            ensureCapacity(value.length());
            builder.append(value);
        }

        private void ensureCapacity(int appendedLength) {
            if (builder.length() + appendedLength > JsonValue.MAX_OUTPUT_CHARS) {
                throw JsonValueException.limit("JSON output exceeds " + JsonValue.MAX_OUTPUT_CHARS + " characters");
            }
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
