package com.tkisor.nekojs.core.module.esm;

public record NekoEsmRuntimeExpression(
        NekoEsmRuntimeExpressionKind kind,
        NekoEsmSpan span,
        String specifier,
        NekoEsmSpan specifierLiteralSpan
) {
    public NekoEsmRuntimeExpression(NekoEsmRuntimeExpressionKind kind, NekoEsmSpan span) {
        this(kind, span, null, null);
    }
}
