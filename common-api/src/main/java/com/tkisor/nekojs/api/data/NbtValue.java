package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 平台无关的 NBT 值模型（sealed 接口），脚本侧类型 {@code NbtValue}。
 *
 * <p>覆盖 NBT 全部标量/数组/列表/复合类型；{@link #kind()} 返回具体类型。所有容器变体
 * 不可变（数组类型防御性拷贝）。常量 {@link #MAX_DEPTH}/{@link #MAX_NODES}/
 * {@link #MAX_STRING_CHARS}/{@link #MAX_OUTPUT_CHARS} 定义序列化与校验时的资源上限。
 */
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

    /** 返回本值的 NBT 类型。 */
    Kind kind();

    /** 构造 byte 值。 */
    static ByteValue byteValue(byte value) { return new ByteValue(value); }
    /** 构造 short 值。 */
    static ShortValue shortValue(short value) { return new ShortValue(value); }
    /** 构造 int 值。 */
    static IntValue intValue(int value) { return new IntValue(value); }
    /** 构造 long 值。 */
    static LongValue longValue(long value) { return new LongValue(value); }
    /** 构造 float 值（须有限）。 */
    static FloatValue floatValue(float value) { return new FloatValue(value); }
    /** 构造 double 值（须有限）。 */
    static DoubleValue doubleValue(double value) { return new DoubleValue(value); }
    /** 构造字符串值。 */
    static StringValue string(String value) { return new StringValue(value); }
    /** 构造字节数组值（防御性拷贝）。 */
    static ByteArrayValue byteArray(byte[] values) { return new ByteArrayValue(values); }
    /** 构造 int 数组值（防御性拷贝）。 */
    static IntArrayValue intArray(int[] values) { return new IntArrayValue(values); }
    /** 构造列表（元素类型由首个元素推断，空列表用 {@link Kind#END}）。 */
    static ListValue list(List<NbtValue> values) { return ListValue.inferred(values); }
    /** 构造 compound 值。 */
    static CompoundValue compound(Map<String, NbtValue> values) { return new CompoundValue(values); }

    /** NBT 类型标识；{@code END} 仅用于空列表。 */
    enum Kind {
        END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY
    }

    /** byte 值。 */
    record ByteValue(byte value) implements NbtValue {
        @Override public Kind kind() { return Kind.BYTE; }
    }

    /** short 值。 */
    record ShortValue(short value) implements NbtValue {
        @Override public Kind kind() { return Kind.SHORT; }
    }

    /** int 值。 */
    record IntValue(int value) implements NbtValue {
        @Override public Kind kind() { return Kind.INT; }
    }

    /** long 值。 */
    record LongValue(long value) implements NbtValue {
        @Override public Kind kind() { return Kind.LONG; }
    }

    /** float 值（须有限）。 */
    record FloatValue(float value) implements NbtValue {
        public FloatValue {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("NBT float must be finite");
        }

        @Override public Kind kind() { return Kind.FLOAT; }
    }

    /** double 值（须有限）。 */
    record DoubleValue(double value) implements NbtValue {
        public DoubleValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("NBT double must be finite");
        }

        @Override public Kind kind() { return Kind.DOUBLE; }
    }

    /** 字符串值。 */
    record StringValue(String value) implements NbtValue {
        public StringValue {
            validateString(value, "NBT string");
        }

        @Override public Kind kind() { return Kind.STRING; }
    }

    /** 字节数组值（不可变，防御性拷贝）。 */
    final class ByteArrayValue implements NbtValue {
        private final byte[] values;

        /** @param values 初始字节数组（防御性拷贝）。 */
        public ByteArrayValue(byte[] values) {
            this.values = Objects.requireNonNull(values, "values").clone();
        }

        /** 返回内部数组的拷贝，避免外部修改破坏不可变性。 */
        public byte[] values() { return values.clone(); }
        @Override public Kind kind() { return Kind.BYTE_ARRAY; }
        @Override public boolean equals(Object other) {
            return other instanceof ByteArrayValue value && Arrays.equals(values, value.values);
        }
        @Override public int hashCode() { return Arrays.hashCode(values); }
    }

    /** int 数组值（不可变，防御性拷贝）。 */
    final class IntArrayValue implements NbtValue {
        private final int[] values;

        /** @param values 初始 int 数组（防御性拷贝）。 */
        public IntArrayValue(int[] values) {
            this.values = Objects.requireNonNull(values, "values").clone();
        }

        /** 返回内部数组的拷贝，避免外部修改破坏不可变性。 */
        public int[] values() { return values.clone(); }
        @Override public Kind kind() { return Kind.INT_ARRAY; }
        @Override public boolean equals(Object other) {
            return other instanceof IntArrayValue value && Arrays.equals(values, value.values);
        }
        @Override public int hashCode() { return Arrays.hashCode(values); }
    }

    /** 列表值（元素须共享同一元素类型）。 */
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

    /** compound 值（不可变）。 */
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
