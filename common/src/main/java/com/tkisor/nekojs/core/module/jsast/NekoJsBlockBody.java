package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsBlockBody(
        NekoEsmSpan span,
        int scopeId,
        List<NekoJsStatement> statements,
        List<NekoJsBinding> bindings,
        List<NekoJsExpression> expressions
) implements NekoJsNode {
    public NekoJsBlockBody {
        statements = statements == null ? List.of() : List.copyOf(statements);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
    }
}
