package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;

import java.util.List;

public interface NbtFacade {
    NbtValue of(NbtValue value);
    NbtValue byteValue(Number value);
    NbtValue shortValue(Number value);
    NbtValue intValue(Number value);
    NbtValue longValue(String value);
    NbtValue floatValue(Number value);
    NbtValue doubleValue(Number value);
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
