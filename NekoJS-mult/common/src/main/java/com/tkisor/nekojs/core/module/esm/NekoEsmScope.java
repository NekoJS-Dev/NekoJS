package com.tkisor.nekojs.core.module.esm;

public record NekoEsmScope(
        int id,
        int parentId,
        NekoEsmScopeKind kind,
        NekoEsmSpan span,
        boolean classBody
) {
    public NekoEsmScope(int id, int parentId, NekoEsmScopeKind kind, NekoEsmSpan span) {
        this(id, parentId, kind, span, false);
    }
}
