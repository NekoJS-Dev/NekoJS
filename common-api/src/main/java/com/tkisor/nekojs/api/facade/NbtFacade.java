package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;

import java.util.List;

public interface NbtFacade {
    NbtValue of(NbtValue value);

    /** 创建空 compound 构建器（JS 侧 {@code NBT.compound} 调用，链式构建后转 NbtValue）。 */
    Object compound();

    // 标量工厂：Java 方法名带 Value 后缀（byte/short/int/long/float/double 是 Java 关键字，
    // 不能作方法名），用 @Remap 映射到 JS 侧短名。@Remap 同时驱动 Graal host access 与
    // ContractReflector 符号 ID——单一真相源。
    @Remap("byte") NbtValue byteValue(Number value);
    @Remap("short") NbtValue shortValue(Number value);
    @Remap("int") NbtValue intValue(Number value);
    @Remap("long") NbtValue longValue(String value);
    @Remap("float") NbtValue floatValue(Number value);
    @Remap("double") NbtValue doubleValue(Number value);
    NbtValue byteArray(List<? extends Number> values);
    NbtValue intArray(List<? extends Number> values);
    String toSnbt(NbtValue value);
    String kind(NbtValue value);
    Object scalar(NbtValue value);
    List<NbtValue> values(NbtValue value);
    List<NbtEntry> entries(NbtValue value);
    NbtValue.CompoundValue read(String path);
    void write(String path, NbtValue value);

    /** 解析 SNBT 字符串为 NbtValue（顶层须为 compound 或 list）。 */
    NbtValue parse(String snbt);

    /** 把 NbtValue 递归转成普通 Java 对象（Map/List/Number/String）。 */
    Object toObject(NbtValue value);

    /** 把普通 Java 对象（Map/List/Number/String/Boolean）递归转成 NbtValue。 */
    NbtValue fromObject(Object value);
}
