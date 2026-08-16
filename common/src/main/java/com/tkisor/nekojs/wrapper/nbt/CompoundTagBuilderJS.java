package com.tkisor.nekojs.wrapper.nbt;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.CompoundBuilder;
import com.tkisor.nekojs.api.data.NbtValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NBT 复合标签构建器（链式，产出不可变 {@link NbtValue.CompoundValue}）。
 *
 * <p>实现 {@link CompoundBuilder} 接口（定义在 common-api），使 {@code NbtFacade.compound()}
 * 能返回有类型的接口而非裸 {@code Object}。
 *
 * <p>脚本通过 {@code NBT.compound()} 或直接 {@code new CompoundTagBuilderJS()} 创建，
 * 用 {@code put/putByte/putInt/.../putCompound/putList} 累积条目，{@code build()}
 * 产出不可变 {@link NbtValue}，可经平台层 adapter / codec 转为 MC {@code CompoundTag}，
 * 或经 {@code NBT.toSnbt} 序列化。
 *
 * <p>放 common 层（无 MC 依赖）；值类型由 {@link NbtValue} sealed record 体系承载。
 */
@Doc("Chainable NBT compound builder producing immutable compound values.")
public final class CompoundTagBuilderJS implements CompoundBuilder {
    private final Map<String, NbtValue> entries = new LinkedHashMap<>();

    /** 创建空构建器。 */
    @Doc("Creates an empty compound builder.")
    public CompoundTagBuilderJS() {}

    /** 放入任意已构建的 {@link NbtValue}。 */
    @Override
    @Doc("Stores an already-built NbtValue under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "value", value = "built NbtValue of any tag type")
    @Return("this, for chaining")
    public CompoundTagBuilderJS put(String key, NbtValue value) {
        entries.put(key, Objects.requireNonNull(value, "value"));
        return this;
    }

    /** 放入 byte 值。 */
    @Override
    @Doc("Stores a byte value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putByte(String key, byte value) {
        return put(key, new NbtValue.ByteValue(value));
    }

    /** 放入 short 值。 */
    @Override
    @Doc("Stores a short value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putShort(String key, short value) {
        return put(key, new NbtValue.ShortValue(value));
    }

    /** 放入 int 值。 */
    @Override
    @Doc("Stores an int value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putInt(String key, int value) {
        return put(key, new NbtValue.IntValue(value));
    }

    /** 放入 long 值。 */
    @Override
    @Doc("Stores a long value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putLong(String key, long value) {
        return put(key, new NbtValue.LongValue(value));
    }

    /** 放入 float 值。 */
    @Override
    @Doc("Stores a float value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putFloat(String key, float value) {
        return put(key, new NbtValue.FloatValue(value));
    }

    /** 放入 double 值。 */
    @Override
    @Doc("Stores a double value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putDouble(String key, double value) {
        return put(key, new NbtValue.DoubleValue(value));
    }

    /** 放入字符串。 */
    @Override
    @Doc("Stores a string value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "value", value = "text value; must not be null")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putString(String key, String value) {
        return put(key, new NbtValue.StringValue(Objects.requireNonNull(value, "value")));
    }

    /** 放入 byte 数组。 */
    @Override
    @Doc("Stores a byte array under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "array elements")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putByteArray(String key, byte... values) {
        return put(key, NbtValue.byteArray(values));
    }

    /** 放入 int 数组。 */
    @Override
    @Doc("Stores an int array under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "array elements")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putIntArray(String key, int... values) {
        return put(key, NbtValue.intArray(values));
    }

    /** 放入嵌套复合标签（由另一个 builder 构建）。 */
    @Override
    @Doc("Stores a nested compound built by another builder under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "builder", value = "builder whose built compound is stored; must not be null")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putCompound(String key, CompoundBuilder builder) {
        return put(key, Objects.requireNonNull(builder, "builder").build());
    }

    /** 放入列表（元素为已构建的 {@link NbtValue}）。 */
    @Override
    @Doc("Stores a list of built NbtValues under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "list elements as built NbtValues")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putList(String key, List<NbtValue> values) {
        return put(key, NbtValue.list(Objects.requireNonNull(values, "values")));
    }

    /** 放入列表（可变参数）。 */
    @Override
    @Doc("Stores a list built from the given NbtValue arguments under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "list elements as built NbtValues")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putList(String key, NbtValue... values) {
        return put(key, NbtValue.list(List.of(Objects.requireNonNull(values, "values"))));
    }

    /** 是否包含指定 key。 */
    @Doc("Checks whether the compound contains an entry for the key.")
    @Param(name = "key", value = "entry key to look up")
    @Return("true when the key is present")
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /** 已收集的条目数。 */
    @Doc("Returns the number of entries collected so far.")
    @Return("entry count")
    public int size() {
        return entries.size();
    }

    /** 构建为不可变 {@link NbtValue.CompoundValue}。 */
    @Doc("Builds the collected entries into a compound value.")
    @Return("immutable snapshot; later builder changes are not reflected")
    public NbtValue.CompoundValue build() {
        return NbtValue.compound(entries);
    }
}
