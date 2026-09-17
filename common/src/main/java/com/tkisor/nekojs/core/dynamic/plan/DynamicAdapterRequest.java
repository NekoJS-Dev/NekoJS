package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

/**
 * 一条 <b>inert</b> Adapter 请求（ticket 16，AC7）：generation-scoped 候选计划对
 * 平台/版本 Adapter 的「将会请求什么」描述。本类<b>没有任何执行通道</b>——不持有
 * registry/supplier/factory 引用，构造与持有都不可能修改 live registry；实际注册
 * 动作由事务/同步 gate（票 21）通过后的 Adapter 激活路径另行裁决。
 *
 * <p>动作集合封闭为 {@code register}：remove/replace/modify 不存在于第一版语义
 * （同 key 定义变化在 preflight 冲突失败，见 {@link DynamicCandidateRegistryPlan}）。
 */
public record DynamicAdapterRequest(
        long generation,
        String registryKey,
        String id,
        DynamicRegisterMode mode,
        String ownerScriptId,
        String fingerprint,
        String action) {

    /** 唯一动作值：注册/重声明（无 remove/replace/modify）。 */
    public static final String ACTION_REGISTER = "register";

    DynamicAdapterRequest(DynamicDefinition definition, long generation, String ownerScriptId) {
        this(generation, definition.type().registryKey(), definition.id(), definition.mode(),
                ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId,
                definition.fingerprint(), ACTION_REGISTER);
    }

    public DynamicAdapterRequest {
        if (!ACTION_REGISTER.equals(action)) {
            throw new IllegalArgumentException(
                    "DynamicAdapterRequest action is closed to '" + ACTION_REGISTER + "'; got '" + action + "'");
        }
    }
}
