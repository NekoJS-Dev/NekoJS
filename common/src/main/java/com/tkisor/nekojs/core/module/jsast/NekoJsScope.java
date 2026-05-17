package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmScopeKind;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

public record NekoJsScope(
        int id,
        int parentId,
        NekoEsmScopeKind kind,
        NekoEsmSpan span
) implements NekoJsNode {}
