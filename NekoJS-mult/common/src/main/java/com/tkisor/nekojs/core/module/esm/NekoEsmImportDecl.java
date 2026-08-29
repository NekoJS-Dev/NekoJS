package com.tkisor.nekojs.core.module.esm;

import java.util.List;

public record NekoEsmImportDecl(
        NekoEsmSpan span,
        String raw,
        String specifier,
        NekoEsmSpan specifierSpan,
        String defaultName,
        String namespaceName,
        List<NekoEsmBinding> namedBindings,
        boolean sideEffectOnly
) implements NekoEsmStatement {
    public NekoEsmImportDecl {
        namedBindings = namedBindings == null ? List.of() : List.copyOf(namedBindings);
    }
}
