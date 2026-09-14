package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 创建 host node 的输入（reconciler → Adapter）。
 *
 * @param primitive 受控 primitive
 * @param key       同一父节点下的稳定身份键，可为 {@code null}
 * @param props     不可变 props 包
 * @param path      节点稳定路径（诊断与 inspector 用）
 */
public record UiHostCreate(UiPrimitive primitive, String key, UiProps props, String path) {

    public UiHostCreate {
        Objects.requireNonNull(primitive, "primitive");
        props = props == null ? UiProps.empty() : props;
        Objects.requireNonNull(path, "path");
    }
}
