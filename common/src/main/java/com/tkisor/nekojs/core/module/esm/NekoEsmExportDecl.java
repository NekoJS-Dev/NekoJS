package com.tkisor.nekojs.core.module.esm;

import java.util.List;

public record NekoEsmExportDecl(
        NekoEsmSpan span,
        String raw,
        NekoEsmExportKind kind,
        String specifier,
        NekoEsmSpan specifierSpan,
        String declarationKind,
        String localName,
        String namespaceName,
        String expression,
        List<NekoEsmBinding> bindings
) implements NekoEsmStatement {
    public NekoEsmExportDecl {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
