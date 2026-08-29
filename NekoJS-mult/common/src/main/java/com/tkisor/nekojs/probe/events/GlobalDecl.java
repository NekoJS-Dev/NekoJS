package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

/**
 * {@code probe.add_global} 事件收集的全局声明：一个名字 + 语言中性的类型。
 * 各 backend 在自己的语言里渲染（TS→{@code declare const Name: T;}、Python→{@code Name: T}）。
 */
public record GlobalDecl(String name, ApiTypeRef type) {
    public GlobalDecl {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("global name must not be blank");
        if (type == null) throw new IllegalArgumentException("global type must not be null");
    }
}
