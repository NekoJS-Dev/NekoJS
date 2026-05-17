package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmBinding;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsImportDeclaration(
        NekoEsmSpan span,
        String raw,
        String specifier,
        NekoEsmSpan specifierSpan,
        String defaultName,
        String namespaceName,
        List<NekoEsmBinding> namedBindings,
        boolean sideEffectOnly
) implements NekoJsStatement {
    public NekoJsImportDeclaration {
        namedBindings = namedBindings == null ? List.of() : List.copyOf(namedBindings);
    }
}
