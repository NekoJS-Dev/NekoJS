package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmRuntimeExpressionKind;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

public record NekoJsRuntimeExpressionStatement(
        NekoEsmSpan span,
        String raw,
        NekoEsmRuntimeExpressionKind kind,
        String specifier,
        NekoEsmSpan specifierLiteralSpan
) implements NekoJsStatement {}
