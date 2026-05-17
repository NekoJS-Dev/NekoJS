package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsExpression(
        NekoEsmSpan span,
        String raw,
        NekoJsExpressionKind kind,
        List<NekoJsExpression> children
) implements NekoJsNode {
    public NekoJsExpression {
        kind = kind == null ? NekoJsExpressionKind.RAW : kind;
        children = children == null ? List.of() : List.copyOf(children);
    }
}
