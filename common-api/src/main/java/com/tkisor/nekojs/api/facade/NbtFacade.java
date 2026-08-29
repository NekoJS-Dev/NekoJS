package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.data.CompoundBuilder;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;

import java.util.List;

/**
 * NBT facade, exposed to scripts as the global object {@code NBT}.
 *
 * <p>Builds and inspects the platform-neutral {@link NbtValue} model: scalar and array
 * factories, SNBT parse/serialize, plain-object conversion, and data-directory file IO.
 * All values are immutable snapshots; file IO is limited to the mod data directory.
 * Invalid input (range overflow, malformed SNBT, non-compound write root) throws
 * {@link com.tkisor.nekojs.api.error.ApiInvocationException} with an
 * {@link com.tkisor.nekojs.api.error.ApiErrorCodes} code.
 */
public interface NbtFacade {
    /** Returns the given value unchanged; lets scripts normalize an existing {@link NbtValue} reference. */
    NbtValue of(NbtValue value);

    /** 创建空 compound 构建器（JS 侧 {@code NBT.compound} 调用，链式构建后转 NbtValue）。 */
    CompoundBuilder compound();

    // 标量工厂：Java 方法名带 Value 后缀（byte/short/int/long/float/double 是 Java 关键字，
    // 不能作方法名），用 @Remap 映射到 JS 侧短名。@Remap 同时驱动 Graal host access 与
    // ContractReflector 符号 ID——单一真相源。
    /** Creates a byte value; the number must be integral and within byte range. */
    @Remap("byte") NbtValue byteValue(Number value);

    /** Creates a short value; the number must be integral and within short range. */
    @Remap("short") NbtValue shortValue(Number value);

    /** Creates an int value; the number must be integral and within int range. */
    @Remap("int") NbtValue intValue(Number value);

    /** Creates a long value from a signed decimal string (JS numbers cannot carry int64 losslessly). */
    @Remap("long") NbtValue longValue(String value);

    /** Creates a float value; the number must be finite and within float range. */
    @Remap("float") NbtValue floatValue(Number value);

    /** Creates a double value; the number must be finite. */
    @Remap("double") NbtValue doubleValue(Number value);

    /** Creates a byte array; every element must be integral and within byte range. */
    NbtValue byteArray(List<? extends Number> values);

    /** Creates an int array; every element must be integral and within int range. */
    NbtValue intArray(List<? extends Number> values);

    /** Serializes the value to an SNBT string. */
    String toSnbt(NbtValue value);

    /** Returns the kind name (e.g. {@code "COMPOUND"}, {@code "BYTE_ARRAY"}) of the value. */
    String kind(NbtValue value);

    /** Unwraps a scalar value to its Java object; long is returned as a {@code String}, non-scalar kinds return {@code null}. */
    Object scalar(NbtValue value);

    /** Returns the children of a list value; returns an empty list for non-list kinds. */
    List<NbtValue> values(NbtValue value);

    /** Returns the key/value entries of a compound value; returns an empty list for non-compound kinds. */
    List<NbtEntry> entries(NbtValue value);

    /** Reads a gzip-compressed compound from the data directory; returns {@code null} when the file does not exist. */
    NbtValue.CompoundValue read(String path);

    /** Writes the value as a gzip-compressed compound to the data directory; the root must be a compound. */
    void write(String path, NbtValue value);

    /** 解析 SNBT 字符串为 NbtValue（顶层须为 compound 或 list）。 */
    NbtValue parse(String snbt);

    /** 把 NbtValue 递归转成普通 Java 对象（Map/List/Number/String）。 */
    Object toObject(NbtValue value);

    /** 把普通 Java 对象（Map/List/Number/String/Boolean）递归转成 NbtValue。 */
    NbtValue fromObject(Object value);
}
