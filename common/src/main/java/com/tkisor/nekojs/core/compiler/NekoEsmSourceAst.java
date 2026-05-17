package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

public record NekoEsmSourceAst(
        NekoSourceFile source,
        String languageId,
        String executableCode,
        String sourceMap,
        NekoEsmModuleAst esmAst
) implements NekoSourceAst {
    public NekoEsmSourceAst {
        languageId = languageId == null || languageId.isBlank() ? "javascript" : languageId;
        executableCode = executableCode == null ? "" : executableCode;
    }
}
