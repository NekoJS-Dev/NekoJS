package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsFunctionLike(
        NekoEsmSpan span,
        String raw,
        String name,
        NekoJsFunctionKind kind,
        List<NekoJsBinding> parameters,
        NekoJsBlockBody body
) implements NekoJsNode {
    public NekoJsFunctionLike {
        kind = kind == null ? NekoJsFunctionKind.FUNCTION : kind;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
