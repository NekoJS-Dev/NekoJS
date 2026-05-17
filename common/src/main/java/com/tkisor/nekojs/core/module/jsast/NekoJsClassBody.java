package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsClassBody(
        NekoEsmSpan span,
        int scopeId,
        String name,
        List<NekoJsClassElement> elements
) implements NekoJsNode {
    public NekoJsClassBody {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }
}
