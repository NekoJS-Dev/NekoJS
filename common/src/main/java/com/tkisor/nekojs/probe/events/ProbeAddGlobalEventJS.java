package com.tkisor.nekojs.probe.events;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code probe.add_global} 事件：脚本登记额外的全局声明（名字 + 类型）。各 backend 在自己的语言里渲染：
 * TS 写到 {@code @manual/globals.d.ts}（{@code declare const Name: T;}），Python 加进 {@code nekojs/__init__.pyi}。
 *
 * <p>类型入参接受字符串（含 {@code .} → Java 全限定名 SYMBOL；否则原始类型名）或 {@link com.tkisor.nekojs.api.surface.ApiTypeRef}。
 */
public final class ProbeAddGlobalEventJS {
    private final List<GlobalDecl> globals = new ArrayList<>();

    /** 登记一个全局：{@code add("MyFlag", "boolean")} / {@code add("Helper", "com.example.Helper")}。 */
    public void add(String name, Object typeDesc) {
        globals.add(new GlobalDecl(name, ProbeModifyTypeEventJS.resolveType(typeDesc)));
    }

    public List<GlobalDecl> globals() {
        return List.copyOf(globals);
    }
}
