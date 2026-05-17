package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoIRBinding;
import com.tkisor.nekojs.api.compiler.NekoIRExport;
import com.tkisor.nekojs.api.compiler.NekoIRImport;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.api.compiler.NekoIRScope;
import com.tkisor.nekojs.api.compiler.NekoUnifiedIR;
import com.tkisor.nekojs.core.module.NekoModuleMode;
import com.tkisor.nekojs.core.module.esm.NekoEsmExportDecl;
import com.tkisor.nekojs.core.module.esm.NekoEsmImportDecl;
import com.tkisor.nekojs.core.module.esm.NekoEsmLocalBinding;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmScope;

import java.util.ArrayList;
import java.util.List;

final class NekoUnifiedIrBuilder {
    private NekoUnifiedIrBuilder() {}

    static NekoUnifiedIR fromEsm(String languageId, String code, String sourceMap, String extension, NekoEsmModuleAst ast) {
        return new NekoUnifiedIR(new NekoIRProgram(languageId, code, sourceMap, requestedMode(extension), ast.module(), ast.topLevelAwait(), imports(ast), exports(ast), bindings(ast), scopes(ast), ast));
    }

    private static NekoModuleMode requestedMode(String extension) {
        return switch (extension) {
            case ".mjs" -> NekoModuleMode.ESM;
            case ".cjs" -> NekoModuleMode.COMMONJS;
            default -> NekoModuleMode.AUTO;
        };
    }

    private static List<NekoIRImport> imports(NekoEsmModuleAst ast) {
        List<NekoIRImport> imports = new ArrayList<>();
        for (NekoEsmImportDecl importDecl : ast.imports()) {
            imports.add(new NekoIRImport(importDecl.specifier(), importDecl));
        }
        return imports;
    }

    private static List<NekoIRExport> exports(NekoEsmModuleAst ast) {
        List<NekoIRExport> exports = new ArrayList<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            exports.add(new NekoIRExport(exportDecl.specifier(), exportDecl));
        }
        return exports;
    }

    private static List<NekoIRBinding> bindings(NekoEsmModuleAst ast) {
        List<NekoIRBinding> bindings = new ArrayList<>();
        for (NekoEsmLocalBinding binding : ast.localBindings()) {
            bindings.add(new NekoIRBinding(binding.name(), binding.kind(), binding.scopeId(), binding));
        }
        return bindings;
    }

    private static List<NekoIRScope> scopes(NekoEsmModuleAst ast) {
        List<NekoIRScope> scopes = new ArrayList<>();
        for (NekoEsmScope scope : ast.scopes()) {
            scopes.add(new NekoIRScope(scope.id(), scope.parentId(), scope));
        }
        return scopes;
    }
}
