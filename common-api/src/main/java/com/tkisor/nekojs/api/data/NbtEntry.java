package com.tkisor.nekojs.api.data;

import java.util.Objects;

public record NbtEntry(String key, NbtValue value) {
    public NbtEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
