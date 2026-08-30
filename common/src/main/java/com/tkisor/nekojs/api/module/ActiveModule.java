package com.tkisor.nekojs.api.module;

import java.util.Objects;

/**
 * 已激活模块：模块描述符 + 模块实现实例。
 *
 * @param descriptor     模块描述符，不能为 {@code null}
 * @param implementation 模块实现实例（可为 {@code null}）
 */
public record ActiveModule(
        ApiModuleDescriptor descriptor,
        Object implementation
) {
    public ActiveModule {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
