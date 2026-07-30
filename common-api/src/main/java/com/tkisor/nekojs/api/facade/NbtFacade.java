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
}
