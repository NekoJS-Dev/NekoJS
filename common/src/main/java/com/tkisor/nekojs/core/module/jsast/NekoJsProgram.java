package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

import java.util.List;

public record NekoJsProgram(
        NekoEsmSpan span,
        boolean module,
        boolean topLevelAwait,
        List<NekoJsStatement> statements,
        List<NekoJsBinding> bindings,
        List<NekoJsScope> scopes,
        List<NekoJsExpression> expressions,
        List<NekoJsFunctionLike> functions,
        List<NekoJsClassBody> classes,
        List<NekoJsBlockBody> blocks
) implements NekoJsNode {
    public NekoJsProgram {
        statements = statements == null ? List.of() : List.copyOf(statements);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        functions = functions == null ? List.of() : List.copyOf(functions);
        classes = classes == null ? List.of() : List.copyOf(classes);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public NekoJsProgram(NekoEsmSpan span, boolean module, boolean topLevelAwait, List<NekoJsStatement> statements, List<NekoJsBinding> bindings, List<NekoJsScope> scopes) {
        this(span, module, topLevelAwait, statements, bindings, scopes, List.of(), List.of(), List.of(), List.of());
    }
}
