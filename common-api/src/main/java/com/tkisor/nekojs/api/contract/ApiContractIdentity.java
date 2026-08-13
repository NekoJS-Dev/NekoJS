package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.Objects;

/**
 * API 契约的稳定身份标识（owner + kind + id + version）。
 *
 * <p>用于索引与查找契约；四个字段均不能为 {@code null}。
 *
 * @param owner      契约所有者（插件/平台标识）
 * @param kind       契约类型
 * @param contractId 契约 id
 * @param version    契约版本
 */
public record ApiContractIdentity(
        String owner,
        ApiContractKind kind,
        String contractId,
        ApiVersion version
) {
    public ApiContractIdentity {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(version, "version");
    }
}
