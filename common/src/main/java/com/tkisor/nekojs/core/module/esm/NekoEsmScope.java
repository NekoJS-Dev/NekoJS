package com.tkisor.nekojs.core.module.esm;

public record NekoEsmScope(
        int id,
        int parentId,
        NekoEsmScopeKind kind,
        NekoEsmSpan span
) {}
