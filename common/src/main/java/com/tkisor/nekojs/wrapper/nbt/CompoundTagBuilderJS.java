package com.tkisor.nekojs.wrapper.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NBT 复合标签构建器（链式，产出不可变 {@link NbtValue.CompoundValue}）。
 *
 * <p>脚本通过 {@code NBT.compound()} 或直接 {@code new CompoundTagBuilderJS()} 创建，
 * 用 {@code put/putByte/putInt/.../putCompound/putList} 累积条目，{@code build()}
 * 产出不可变 {@link NbtValue}，可经平台层 adapter / codec 转为 MC {@code CompoundTag}，
 * 或经 {@code NBT.toSnbt} 序列化。
 *
 * <p>放 common 层（无 MC 依赖）；值类型由 {@link NbtValue} sealed record 体系承载。
 */
public final class CompoundTagBuilderJS {
    private final Map<String, NbtValue> entries = new LinkedHashMap<>();

    public CompoundTagBuilderJS() {}

    /** 放入任意已构建的 {@link NbtValue}。 */
    public CompoundTagBuilderJS put(String key, NbtValue value) {
        entries.put(key, Objects.requireNonNull(value, "value"));
        return this;
    }

    public CompoundTagBuilderJS putByte(String key, byte value) {
        return put(key, new NbtValue.ByteValue(value));
    }

    public CompoundTagBuilderJS putShort(String key, short value) {
        return put(key, new NbtValue.ShortValue(value));
    }

    public CompoundTagBuilderJS putInt(String key, int value) {
        return put(key, new NbtValue.IntValue(value));
    }

    public CompoundTagBuilderJS putLong(String key, long value) {
        return put(key, new NbtValue.LongValue(value));
    }

    public CompoundTagBuilderJS putFloat(String key, float value) {
        return put(key, new NbtValue.FloatValue(value));
    }

    public CompoundTagBuilderJS putDouble(String key, double value) {
        return put(key, new NbtValue.DoubleValue(value));
    }

    public CompoundTagBuilderJS putString(String key, String value) {
        return put(key, new NbtValue.StringValue(Objects.requireNonNull(value, "value")));
    }

    public CompoundTagBuilderJS putByteArray(String key, byte... values) {
        return put(key, NbtValue.byteArray(values));
    }

    public CompoundTagBuilderJS putIntArray(String key, int... values) {
        return put(key, NbtValue.intArray(values));
    }

    /** 放入嵌套复合标签（由另一个 builder 构建）。 */
    public CompoundTagBuilderJS putCompound(String key, CompoundTagBuilderJS builder) {
        return put(key, Objects.requireNonNull(builder, "builder").build());
    }

    /** 放入列表（元素为已构建的 {@link NbtValue}）。 */
    public CompoundTagBuilderJS putList(String key, List<NbtValue> values) {
        return put(key, NbtValue.list(Objects.requireNonNull(values, "values")));
    }

    /** 放入列表（可变参数）。 */
    public CompoundTagBuilderJS putList(String key, NbtValue... values) {
        return put(key, NbtValue.list(List.of(Objects.requireNonNull(values, "values"))));
    }

    /** 是否包含指定 key。 */
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /** 已收集的条目数。 */
    public int size() {
        return entries.size();
    }

    /** 构建为不可变 {@link NbtValue.CompoundValue}。 */
    public NbtValue.CompoundValue build() {
        return NbtValue.compound(entries);
    }
}
