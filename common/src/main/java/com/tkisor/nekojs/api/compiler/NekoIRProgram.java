package com.tkisor.nekojs.api.compiler;

import com.tkisor.nekojs.core.module.NekoModuleMode;

import java.util.List;

public record NekoIRProgram(
        String languageId,
        String code,
        String sourceMap,
        NekoModuleMode requestedMode,
        boolean module,
        boolean topLevelAwait,
        List<NekoIRImport> imports,
        List<NekoIRExport> exports,
        List<NekoIRBinding> bindings,
        List<NekoIRScope> scopes
) implements NekoIRNode {
    public NekoIRProgram {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        code = code == null ? "" : code;
        requestedMode = requestedMode == null ? NekoModuleMode.AUTO : requestedMode;
        imports = imports == null ? List.of() : List.copyOf(imports);
        exports = exports == null ? List.of() : List.copyOf(exports);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
