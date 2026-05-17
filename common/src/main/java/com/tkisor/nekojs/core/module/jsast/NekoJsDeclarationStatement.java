package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsDeclarationStatement(
        NekoEsmSpan span,
        String raw,
        String kind,
        List<NekoJsBinding> bindings
) implements NekoJsStatement {
    public NekoJsDeclarationStatement {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
