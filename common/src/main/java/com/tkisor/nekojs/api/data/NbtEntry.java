package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Objects;

/**
 * One key/value entry of an NBT compound, exposed to scripts as {@code NbtEntry}.
 *
 * @param key   member name; must not be {@code null}
 * @param value member value; must not be {@code null}
 */
@ContractReceiver
public record NbtEntry(String key, NbtValue value) {
    public NbtEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
