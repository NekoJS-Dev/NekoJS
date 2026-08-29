package com.tkisor.nekojs.core.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

import java.util.Map;

public final class NbtSnbtSerializer {
    private NbtSnbtSerializer() {
    }

    public static String serialize(NbtValue value) {
        if (value == null) throw new IllegalArgumentException("NBT value cannot be null");
        Output output = new Output();
        write(value, output, 0, new Counter());
        return output.toString();
    }

    private static void write(NbtValue value, Output output, int depth, Counter counter) {
        if (depth > NbtValue.MAX_DEPTH) throw new IllegalArgumentException("NBT nesting exceeds " + NbtValue.MAX_DEPTH);
        counter.increment();
        if (value instanceof NbtValue.ByteValue number) output.append(Byte.toString(number.value())).append('b');
        else if (value instanceof NbtValue.ShortValue number) output.append(Short.toString(number.value())).append('s');
        else if (value instanceof NbtValue.IntValue number) output.append(Integer.toString(number.value()));
        else if (value instanceof NbtValue.LongValue number) output.append(Long.toString(number.value())).append('l');
        else if (value instanceof NbtValue.FloatValue number) output.append(Float.toString(number.value())).append('f');
        else if (value instanceof NbtValue.DoubleValue number) output.append(Double.toString(number.value())).append('d');
        else if (value instanceof NbtValue.StringValue string) writeQuoted(string.value(), output);
        else if (value instanceof NbtValue.ByteArrayValue array) writeByteArray(array.values(), output);
        else if (value instanceof NbtValue.IntArrayValue array) writeIntArray(array.values(), output);
        else if (value instanceof NbtValue.ListValue list) writeList(list, output, depth, counter);
        else writeCompound((NbtValue.CompoundValue) value, output, depth, counter);
    }

    private static void writeByteArray(byte[] values, Output output) {
        output.append("[B;");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) output.append(',');
            output.append(Byte.toString(values[index])).append('B');
        }
        output.append(']');
    }

    private static void writeIntArray(int[] values, Output output) {
        output.append("[I;");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) output.append(',');
            output.append(Integer.toString(values[index]));
        }
        output.append(']');
    }

    private static void writeList(NbtValue.ListValue list, Output output, int depth, Counter counter) {
        output.append('[');
        for (int index = 0; index < list.values().size(); index++) {
            if (index > 0) output.append(',');
            write(list.values().get(index), output, depth + 1, counter);
        }
        output.append(']');
    }

    private static void writeCompound(NbtValue.CompoundValue compound, Output output, int depth, Counter counter) {
        output.append('{');
        int index = 0;
        for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
            if (index++ > 0) output.append(',');
            if (entry.getKey().matches("[A-Za-z0-9._+-]+")) output.append(entry.getKey());
            else writeQuoted(entry.getKey(), output);
            output.append(':');
            write(entry.getValue(), output, depth + 1, counter);
        }
        output.append('}');
    }

    private static void writeQuoted(String value, Output output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                default -> output.append(current);
            }
        }
        output.append('"');
    }

    private static final class Counter {
        private int nodes;

        void increment() {
            if (++nodes > NbtValue.MAX_NODES) throw new IllegalArgumentException("NBT contains more than " + NbtValue.MAX_NODES + " values");
        }
    }

    private static final class Output {
        private final StringBuilder builder = new StringBuilder();

        Output append(char value) {
            ensureCapacity(1);
            builder.append(value);
            return this;
        }

        Output append(String value) {
            ensureCapacity(value.length());
            builder.append(value);
            return this;
        }

        private void ensureCapacity(int appendedLength) {
            if (builder.length() + appendedLength > NbtValue.MAX_OUTPUT_CHARS) {
                throw new IllegalArgumentException("NBT SNBT output exceeds " + NbtValue.MAX_OUTPUT_CHARS + " characters");
            }
        }

        @Override public String toString() { return builder.toString(); }
    }
}
