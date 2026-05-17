package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.jsast.NekoJsBinding;
import com.tkisor.nekojs.core.module.jsast.NekoJsBlockBody;
import com.tkisor.nekojs.core.module.jsast.NekoJsClassBody;
import com.tkisor.nekojs.core.module.jsast.NekoJsExportDeclaration;
import com.tkisor.nekojs.core.module.jsast.NekoJsExpression;
import com.tkisor.nekojs.core.module.jsast.NekoJsExpressionKind;
import com.tkisor.nekojs.core.module.jsast.NekoJsFunctionKind;
import com.tkisor.nekojs.core.module.jsast.NekoJsFunctionLike;
import com.tkisor.nekojs.core.module.jsast.NekoJsImportDeclaration;
import com.tkisor.nekojs.core.module.jsast.NekoJsProgram;
import com.tkisor.nekojs.core.module.jsast.NekoJsRuntimeExpressionStatement;
import com.tkisor.nekojs.core.module.jsast.NekoJsScope;
import com.tkisor.nekojs.core.module.jsast.NekoJsStatement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record NekoEsmModuleAst(
        boolean module,
        boolean topLevelAwait,
        List<NekoEsmStatement> statements,
        List<NekoEsmRuntimeExpression> runtimeExpressions,
        List<NekoEsmLocalBinding> localBindings,
        List<NekoEsmScope> scopes,
        NekoJsProgram program
) {
    public NekoEsmModuleAst {
        statements = statements == null ? List.of() : List.copyOf(statements);
        runtimeExpressions = runtimeExpressions == null ? List.of() : List.copyOf(runtimeExpressions);
        localBindings = localBindings == null ? List.of() : List.copyOf(localBindings);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        program = program == null ? toProgram(module, topLevelAwait, statements, runtimeExpressions, localBindings, scopes) : program;
    }

    public NekoEsmModuleAst(boolean module, boolean topLevelAwait, List<NekoEsmStatement> statements, List<NekoEsmRuntimeExpression> runtimeExpressions, List<NekoEsmLocalBinding> localBindings, List<NekoEsmScope> scopes) {
        this(module, topLevelAwait, statements, runtimeExpressions, localBindings, scopes, null);
    }

    public List<NekoEsmImportDecl> imports() {
        List<NekoEsmImportDecl> imports = new ArrayList<>();
        for (NekoEsmStatement statement : statements) {
            if (statement instanceof NekoEsmImportDecl importDecl) {
                imports.add(importDecl);
            }
        }
        return List.copyOf(imports);
    }

    public List<NekoEsmExportDecl> exports() {
        List<NekoEsmExportDecl> exports = new ArrayList<>();
        for (NekoEsmStatement statement : statements) {
            if (statement instanceof NekoEsmExportDecl exportDecl) {
                exports.add(exportDecl);
            }
        }
        return List.copyOf(exports);
    }

    public boolean hasLocalBinding(String name) {
        if (name == null || name.isBlank()) return false;
        for (NekoEsmLocalBinding binding : localBindings) {
            if (binding.scopeId() == 0 && name.equals(binding.name())) {
                return true;
            }
        }
        return false;
    }

    public List<NekoEsmStatement> staticDependencies() {
        List<NekoEsmStatement> dependencies = new ArrayList<>();
        for (NekoEsmStatement statement : statements) {
            if (specifier(statement) != null) {
                dependencies.add(statement);
            }
        }
        return List.copyOf(dependencies);
    }

    public List<String> staticDependencySpecifiers() {
        Set<String> specifiers = new LinkedHashSet<>();
        for (NekoEsmStatement statement : staticDependencies()) {
            specifiers.add(specifier(statement));
        }
        return List.copyOf(specifiers);
    }

    private String specifier(NekoEsmStatement statement) {
        if (statement instanceof NekoEsmImportDecl importDecl) {
            return importDecl.specifier();
        }
        if (statement instanceof NekoEsmExportDecl exportDecl) {
            return exportDecl.specifier();
        }
        return null;
    }

    private static NekoJsProgram toProgram(boolean module, boolean topLevelAwait, List<NekoEsmStatement> statements, List<NekoEsmRuntimeExpression> runtimeExpressions, List<NekoEsmLocalBinding> localBindings, List<NekoEsmScope> scopes) {
        List<NekoJsStatement> jsStatements = new ArrayList<>();
        for (NekoEsmStatement statement : statements) {
            if (statement instanceof NekoEsmImportDecl importDecl) {
                jsStatements.add(new NekoJsImportDeclaration(importDecl.span(), importDecl.raw(), importDecl.specifier(), importDecl.specifierSpan(), importDecl.defaultName(), importDecl.namespaceName(), importDecl.namedBindings(), importDecl.sideEffectOnly()));
            } else if (statement instanceof NekoEsmExportDecl exportDecl) {
                jsStatements.add(new NekoJsExportDeclaration(exportDecl.span(), exportDecl.raw(), exportDecl.kind(), exportDecl.specifier(), exportDecl.specifierSpan(), exportDecl.declarationKind(), exportDecl.localName(), exportDecl.namespaceName(), exportDecl.expression(), exportDecl.bindings()));
            }
        }
        List<NekoJsExpression> jsExpressions = new ArrayList<>();
        for (NekoEsmRuntimeExpression expression : runtimeExpressions) {
            jsStatements.add(new NekoJsRuntimeExpressionStatement(expression.span(), null, expression.kind(), expression.specifier(), expression.specifierLiteralSpan()));
            jsExpressions.add(new NekoJsExpression(expression.span(), null, expressionKind(expression.kind()), List.of()));
        }
        List<NekoJsBinding> jsBindings = new ArrayList<>();
        for (NekoEsmLocalBinding binding : localBindings) {
            jsBindings.add(new NekoJsBinding(binding.name(), binding.kind(), binding.source(), binding.span(), binding.scopeId()));
        }
        List<NekoJsScope> jsScopes = new ArrayList<>();
        List<NekoJsBlockBody> jsBlocks = new ArrayList<>();
        List<NekoJsFunctionLike> jsFunctions = new ArrayList<>();
        List<NekoJsClassBody> jsClasses = new ArrayList<>();
        for (NekoEsmScope scope : scopes) {
            NekoJsScope jsScope = new NekoJsScope(scope.id(), scope.parentId(), scope.kind(), scope.span());
            jsScopes.add(jsScope);
            List<NekoJsBinding> scopeBindings = bindingsForScope(jsBindings, scope.id());
            NekoJsBlockBody block = new NekoJsBlockBody(scope.span(), scope.id(), List.of(), scopeBindings, expressionsInside(jsExpressions, scope.span()));
            jsBlocks.add(block);
            if (scope.kind() == NekoEsmScopeKind.FUNCTION) {
                jsFunctions.add(new NekoJsFunctionLike(scope.span(), null, null, NekoJsFunctionKind.FUNCTION, parameterBindings(scopeBindings), block));
            } else if (classBodyScope(scope)) {
                jsClasses.add(new NekoJsClassBody(scope.span(), scope.id(), null, List.of()));
            }
        }
        return new NekoJsProgram(new NekoEsmSpan(0, scopes.isEmpty() ? 0 : scopes.get(0).span().end()), module, topLevelAwait, jsStatements, jsBindings, jsScopes, jsExpressions, jsFunctions, jsClasses, jsBlocks);
    }

    private static NekoJsExpressionKind expressionKind(NekoEsmRuntimeExpressionKind kind) {
        if (kind == NekoEsmRuntimeExpressionKind.DYNAMIC_IMPORT) return NekoJsExpressionKind.DYNAMIC_IMPORT;
        return NekoJsExpressionKind.IMPORT_META;
    }

    private static List<NekoJsBinding> bindingsForScope(List<NekoJsBinding> bindings, int scopeId) {
        List<NekoJsBinding> result = new ArrayList<>();
        for (NekoJsBinding binding : bindings) {
            if (binding.scopeId() == scopeId) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    private static List<NekoJsBinding> parameterBindings(List<NekoJsBinding> bindings) {
        List<NekoJsBinding> result = new ArrayList<>();
        for (NekoJsBinding binding : bindings) {
            if ("param".equals(binding.kind())) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    private static List<NekoJsExpression> expressionsInside(List<NekoJsExpression> expressions, NekoEsmSpan span) {
        List<NekoJsExpression> result = new ArrayList<>();
        for (NekoJsExpression expression : expressions) {
            if (span != null && expression.span() != null && expression.span().start() >= span.start() && expression.span().end() <= span.end()) {
                result.add(expression);
            }
        }
        return List.copyOf(result);
    }

    private static boolean classBodyScope(NekoEsmScope scope) {
        return scope.classBody();
    }
}
