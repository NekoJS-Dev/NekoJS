package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.module.NekoModuleMode;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

final class NekoUnifiedIrBuilder {
    private NekoUnifiedIrBuilder() {}

    static NekoIRProgram fromEsm(String languageId, String code, String sourceMap, String extension, NekoEsmModuleAst ast) {
        return new NekoIRProgram(languageId, code, sourceMap, NekoModuleMode.fromExtension(extension), ast.module(), ast.topLevelAwait());
    }
}
