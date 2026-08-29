package com.tkisor.nekojs.api.data;

import java.util.List;

/**
 * NBT 复合标签构建器接口（链式，产出不可变 {@link NbtValue.CompoundValue}）。
 *
 * <p>定义在 common-api 以便 {@code NbtFacade.compound()} 能返回有类型的接口，
 * 而非裸 {@code Object}（避免类型泄漏）。实现 {@link com.tkisor.nekojs.wrapper.nbt.CompoundTagBuilderJS}
 * 在 common 层。
 *
 * <p>脚本通过 {@code NBT.compound()} 创建，用 {@code put/putByte/.../build()} 累积条目。
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

    /** 放入嵌套复合标签（由另一个 builder 构建）。 */
    CompoundBuilder putCompound(String key, CompoundBuilder builder);

    /** 放入列表（元素为已构建的 {@link NbtValue}）。 */
    CompoundBuilder putList(String key, List<NbtValue> values);

    /** 放入列表（可变参数）。 */
    CompoundBuilder putList(String key, NbtValue... values);

    /** 是否包含指定 key。 */
    boolean contains(String key);

    /** 已收集的条目数。 */
    int size();

    /** 构建为不可变 {@link NbtValue.CompoundValue}。 */
    NbtValue.CompoundValue build();
}
