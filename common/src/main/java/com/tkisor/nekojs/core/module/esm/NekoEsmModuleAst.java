package com.tkisor.nekojs.core.module.esm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record NekoEsmModuleAst(
        boolean module,
        boolean topLevelAwait,
        List<NekoEsmStatement> statements,
        List<NekoEsmRuntimeExpression> runtimeExpressions,
        List<NekoEsmLocalBinding> localBindings
) {
    public NekoEsmModuleAst {
        statements = statements == null ? List.of() : List.copyOf(statements);
        runtimeExpressions = runtimeExpressions == null ? List.of() : List.copyOf(runtimeExpressions);
        localBindings = localBindings == null ? List.of() : List.copyOf(localBindings);
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
            if (name.equals(binding.name())) {
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
}
