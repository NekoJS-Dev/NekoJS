package com.tkisor.nekojs.api.data;

import java.util.List;

/**
 * Interface for building an NBT compound tag. The methods chain and produce an immutable
 * {@link NbtValue.CompoundValue}.
 *
 * <p>Declared in common-api so {@code NbtFacade.compound()} can return a typed interface
 * instead of a bare {@code Object}, which avoids leaking the type. The implementation
 * {@link com.tkisor.nekojs.wrapper.nbt.CompoundTagBuilderJS} lives in the common layer.
 *
 * <p>Scripts create the builder with {@code NBT.compound()} and add entries with
 * {@code put/putByte/.../build()}.
 */
public interface CompoundBuilder {

    CompoundBuilder put(String key, NbtValue value);

    CompoundBuilder putByte(String key, byte value);

    CompoundBuilder putShort(String key, short value);

    CompoundBuilder putInt(String key, int value);

    CompoundBuilder putLong(String key, long value);

    CompoundBuilder putFloat(String key, float value);

    CompoundBuilder putDouble(String key, double value);

    CompoundBuilder putString(String key, String value);

    CompoundBuilder putByteArray(String key, byte... values);

    CompoundBuilder putIntArray(String key, int... values);

    /** Puts a nested compound tag built by another builder. */
    CompoundBuilder putCompound(String key, CompoundBuilder builder);

    /** Puts a list whose elements are already built {@link NbtValue} instances. */
    CompoundBuilder putList(String key, List<NbtValue> values);

    /** Puts a list from varargs. */
    CompoundBuilder putList(String key, NbtValue... values);

    /** Returns whether the given key has been added. */
    boolean contains(String key);

    /** Returns the number of entries collected so far. */
    int size();

    /** Builds the immutable {@link NbtValue.CompoundValue}. */
    NbtValue.CompoundValue build();
}
