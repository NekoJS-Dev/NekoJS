package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ContractReceiver
public sealed interface NbtValue permits
        NbtValue.ByteValue,
        NbtValue.ShortValue,
        NbtValue.IntValue,
        NbtValue.LongValue,
        NbtValue.FloatValue,
        NbtValue.DoubleValue,
        NbtValue.StringValue,
        NbtValue.ByteArrayValue,
        NbtValue.IntArrayValue,
        NbtValue.ListValue,
        NbtValue.CompoundValue {

    int MAX_DEPTH = 64;
    int MAX_NODES = 10_000;
    int MAX_STRING_CHARS = 1_048_576;
    int MAX_OUTPUT_CHARS = 1_048_576;

    Kind kind();

    static ByteValue byteValue(byte value) { return new ByteValue(value); }
    static ShortValue shortValue(short value) { return new ShortValue(value); }
    static IntValue intValue(int value) { return new IntValue(value); }
    static LongValue longValue(long value) { return new LongValue(value); }
    static FloatValue floatValue(float value) { return new FloatValue(value); }
    static DoubleValue doubleValue(double value) { return new DoubleValue(value); }
    static StringValue string(String value) { return new StringValue(value); }
    static ByteArrayValue byteArray(byte[] values) { return new ByteArrayValue(values); }
    static IntArrayValue intArray(int[] values) { return new IntArrayValue(values); }
    static ListValue list(List<NbtValue> values) { return ListValue.inferred(values); }
    static CompoundValue compound(Map<String, NbtValue> values) { return new CompoundValue(values); }

    enum Kind {
        END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY
    }

    record ByteValue(byte value) implements NbtValue {
        @Override public Kind kind() { return Kind.BYTE; }
    }

    record ShortValue(short value) implements NbtValue {
        @Override public Kind kind() { return Kind.SHORT; }
    }

    record IntValue(int value) implements NbtValue {
        @Override public Kind kind() { return Kind.INT; }
    }

    record LongValue(long value) implements NbtValue {
        @Override public Kind kind() { return Kind.LONG; }
    }

    record FloatValue(float value) implements NbtValue {
        public FloatValue {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("NBT float must be finite");
        }

        @Override public Kind kind() { return Kind.FLOAT; }
    }

    record DoubleValue(double value) implements NbtValue {
        public DoubleValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("NBT double must be finite");
        }

        @Override public Kind kind() { return Kind.DOUBLE; }
    }

    record StringValue(String value) implements NbtValue {
        public StringValue {
            validateString(value, "NBT string");
        }

        @Override public Kind kind() { return Kind.STRING; }
    }

    final class ByteArrayValue implements NbtValue {
        private final byte[] values;

        public ByteArrayValue(byte[] values) {
            this.values = Objects.requireNonNull(values, "values").clone();
        }

        public byte[] values() { return values.clone(); }
        @Override public Kind kind() { return Kind.BYTE_ARRAY; }
        @Override public boolean equals(Object other) {
            return other instanceof ByteArrayValue value && Arrays.equals(values, value.values);
        }
        @Override public int hashCode() { return Arrays.hashCode(values); }
    }

    final class IntArrayValue implements NbtValue {
        private final int[] values;

        public IntArrayValue(int[] values) {
            this.values = Objects.requireNonNull(values, "values").clone();
        }

        public int[] values() { return values.clone(); }
        @Override public Kind kind() { return Kind.INT_ARRAY; }
        @Override public boolean equals(Object other) {
            return other instanceof IntArrayValue value && Arrays.equals(values, value.values);
        }
        @Override public int hashCode() { return Arrays.hashCode(values); }
    }

    record ListValue(Kind elementKind, List<NbtValue> values) implements NbtValue {
        public ListValue {
            Objects.requireNonNull(elementKind, "elementKind");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.isEmpty()) {
                if (elementKind != Kind.END) throw new IllegalArgumentException("empty NBT list must use END element kind");
            } else {
                if (elementKind == Kind.END) throw new IllegalArgumentException("non-empty NBT list cannot use END element kind");
                values.forEach(value -> {
                    Objects.requireNonNull(value, "NBT list value");
                    if (value.kind() != elementKind) {
                        throw new IllegalArgumentException("NBT list values must share one element kind");
                    }
                });
            }
        }

        static ListValue inferred(List<NbtValue> values) {
            List<NbtValue> copy = List.copyOf(Objects.requireNonNull(values, "values"));
            return new ListValue(copy.isEmpty() ? Kind.END : copy.getFirst().kind(), copy);
        }

        @Override public Kind kind() { return Kind.LIST; }
    }

    record CompoundValue(Map<String, NbtValue> values) implements NbtValue {
        public CompoundValue {
            Objects.requireNonNull(values, "values");
            Map<String, NbtValue> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                validateString(key, "NBT compound key");
                copy.put(key, Objects.requireNonNull(value, "NBT compound value"));
            });
            values = Collections.unmodifiableMap(copy);
        }

        @Override public Kind kind() { return Kind.COMPOUND; }
    }

    private static void validateString(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > MAX_STRING_CHARS) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_STRING_CHARS + " characters");
        }
    }
}
