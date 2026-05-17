package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

public record NekoJsClassElement(
        NekoEsmSpan span,
        String raw,
        String name,
        NekoJsClassElementKind kind,
        boolean isStatic,
        boolean isPrivate,
        boolean computed,
        NekoJsFunctionLike function,
        NekoJsExpression initializer
) implements NekoJsNode {
    public NekoJsClassElement {
        kind = kind == null ? NekoJsClassElementKind.UNKNOWN : kind;
    }
}
