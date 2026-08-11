package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Objects;

@ContractReceiver
public record NbtEntry(String key, NbtValue value) {
    public NbtEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
