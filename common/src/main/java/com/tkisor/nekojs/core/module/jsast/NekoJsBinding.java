package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmBindingSource;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

public record NekoJsBinding(
        String name,
        String kind,
        NekoEsmBindingSource source,
        NekoEsmSpan span,
        int scopeId
) implements NekoJsNode {}
