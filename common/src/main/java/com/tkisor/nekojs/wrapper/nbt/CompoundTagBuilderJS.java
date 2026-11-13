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
 * Chainable builder for NBT compound tags. It produces an immutable
 * {@link NbtValue.CompoundValue}.
 *
 * <p>It implements the {@link CompoundBuilder} interface declared in common-api, so
 * {@code NbtFacade.compound()} can return a typed interface instead of a bare {@code Object}.
 *
 * <p>Scripts create one with {@code NBT.compound()} or {@code new CompoundTagBuilderJS()}, add
 * entries with {@code put/putByte/putInt/.../putCompound/putList}, and call {@code build()} to
 * get an immutable {@link NbtValue}. A platform adapter or codec can turn that value into an MC
 * {@code CompoundTag}, or {@code NBT.toSnbt} can serialize it.
 *
 * <p>Lives in common and has no MC dependency. The value types come from the sealed record
 * hierarchy of {@link NbtValue}.
 */
@Doc("Chainable NBT compound builder producing immutable compound values.")
public final class CompoundTagBuilderJS implements CompoundBuilder {
    private final Map<String, NbtValue> entries = new LinkedHashMap<>();

    /** Creates an empty builder. */
    @Doc("Creates an empty compound builder.")
    public CompoundTagBuilderJS() {}

    /** Puts an already-built {@link NbtValue} under the key. */
    @Override
    @Doc("Stores an already-built NbtValue under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "value", value = "built NbtValue of any tag type")
    @Return("this, for chaining")
    public CompoundTagBuilderJS put(String key, NbtValue value) {
        entries.put(key, Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Puts a byte value. */
    @Override
    @Doc("Stores a byte value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putByte(String key, byte value) {
        return put(key, new NbtValue.ByteValue(value));
    }

    /** Puts a short value. */
    @Override
    @Doc("Stores a short value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putShort(String key, short value) {
        return put(key, new NbtValue.ShortValue(value));
    }

    /** Puts an int value. */
    @Override
    @Doc("Stores an int value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putInt(String key, int value) {
        return put(key, new NbtValue.IntValue(value));
    }

    /** Puts a long value. */
    @Override
    @Doc("Stores a long value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putLong(String key, long value) {
        return put(key, new NbtValue.LongValue(value));
    }

    /** Puts a float value. */
    @Override
    @Doc("Stores a float value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putFloat(String key, float value) {
        return put(key, new NbtValue.FloatValue(value));
    }

    /** Puts a double value. */
    @Override
    @Doc("Stores a double value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putDouble(String key, double value) {
        return put(key, new NbtValue.DoubleValue(value));
    }

    /** Puts a string value. */
    @Override
    @Doc("Stores a string value under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "value", value = "text value; must not be null")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putString(String key, String value) {
        return put(key, new NbtValue.StringValue(Objects.requireNonNull(value, "value")));
    }

    /** Puts a byte array. */
    @Override
    @Doc("Stores a byte array under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "array elements")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putByteArray(String key, byte... values) {
        return put(key, NbtValue.byteArray(values));
    }

    /** Puts an int array. */
    @Override
    @Doc("Stores an int array under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "array elements")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putIntArray(String key, int... values) {
        return put(key, NbtValue.intArray(values));
    }

    /** Puts a nested compound tag, built by another builder. */
    @Override
    @Doc("Stores a nested compound built by another builder under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "builder", value = "builder whose built compound is stored; must not be null")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putCompound(String key, CompoundBuilder builder) {
        return put(key, Objects.requireNonNull(builder, "builder").build());
    }

    /** Puts a list whose elements are already-built {@link NbtValue}s. */
    @Override
    @Doc("Stores a list of built NbtValues under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "list elements as built NbtValues")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putList(String key, List<NbtValue> values) {
        return put(key, NbtValue.list(Objects.requireNonNull(values, "values")));
    }

    /** Puts a list passed as varargs. */
    @Override
    @Doc("Stores a list built from the given NbtValue arguments under the key.")
    @Param(name = "key", value = "entry key; overwrites an existing entry with the same key")
    @Param(name = "values", value = "list elements as built NbtValues")
    @Return("this, for chaining")
    public CompoundTagBuilderJS putList(String key, NbtValue... values) {
        return put(key, NbtValue.list(List.of(Objects.requireNonNull(values, "values"))));
    }

    /** Tells whether an entry with the key exists. */
    @Doc("Checks whether the compound contains an entry for the key.")
    @Param(name = "key", value = "entry key to look up")
    @Return("true when the key is present")
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /** Returns the number of entries collected so far. */
    @Doc("Returns the number of entries collected so far.")
    @Return("entry count")
    public int size() {
        return entries.size();
    }

    /** Builds the entries into an immutable {@link NbtValue.CompoundValue}. */
    @Doc("Builds the collected entries into a compound value.")
    @Return("immutable snapshot; later builder changes are not reflected")
    public NbtValue.CompoundValue build() {
        return NbtValue.compound(entries);
    }
}
