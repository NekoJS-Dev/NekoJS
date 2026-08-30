package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

/**
 * API 契约哈希快照：portable API 版本与契约哈希，以及各模块的契约哈希。
 *
 * @param portableApiVersion    portable API 版本字符串
 * @param portableContractHash  portable 契约哈希
 * @param moduleContractHashes  模块 id → 契约哈希（拷贝为不可变映射）
 */
public record ApiContractHashes(
        String portableApiVersion,
        String portableContractHash,
        Map<String, String> moduleContractHashes
) {
    public ApiContractHashes {
        moduleContractHashes = Map.copyOf(moduleContractHashes == null ? Map.of() : moduleContractHashes);
    }
}
