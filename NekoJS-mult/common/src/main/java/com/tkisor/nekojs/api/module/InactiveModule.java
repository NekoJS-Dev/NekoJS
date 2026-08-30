package com.tkisor.nekojs.api.module;

import java.util.Objects;

/**
 * 未激活模块：模块描述符 + 未激活原因 + 详情。
 *
 * @param descriptor 模块描述符，不能为 {@code null}
 * @param reason     未激活原因，不能为 {@code null}
 * @param detail     详情说明（可为 {@code null}）
 */
public record InactiveModule(
        ApiModuleDescriptor descriptor,
        InactiveReason reason,
        String detail
) {
    public InactiveModule {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reason, "reason");
    }
}
