package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Objects;

/**
 * NBT compound 的一个键值条目（脚本侧类型 {@code NbtEntry}）。
 *
 * @param key   键名，不能为 {@code null}
 * @param value 值，不能为 {@code null}
 */
@ContractReceiver
public record NbtEntry(String key, NbtValue value) {
    public NbtEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
