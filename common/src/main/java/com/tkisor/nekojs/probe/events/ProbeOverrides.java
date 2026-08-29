package com.tkisor.nekojs.probe.events;

import java.util.List;

/**
 * {@code probe.add_global} 与 {@code probe.snippets} 事件收集结果的不可变捆绑，经
 * {@link com.tkisor.nekojs.probe.ProbeContext#overrides()} 传给各 backend。
 */
public record ProbeOverrides(List<GlobalDecl> globals, List<Snippet> snippets) {
    public ProbeOverrides {
        globals = List.copyOf(globals == null ? List.of() : globals);
        snippets = List.copyOf(snippets == null ? List.of() : snippets);
    }

    public static ProbeOverrides empty() {
        return new ProbeOverrides(List.of(), List.of());
    }
}
