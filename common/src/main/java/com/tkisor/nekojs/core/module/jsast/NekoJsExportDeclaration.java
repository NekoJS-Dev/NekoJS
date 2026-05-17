package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmBinding;
import com.tkisor.nekojs.core.module.esm.NekoEsmExportKind;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsExportDeclaration(
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
) implements NekoJsStatement {
    public NekoJsExportDeclaration {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
