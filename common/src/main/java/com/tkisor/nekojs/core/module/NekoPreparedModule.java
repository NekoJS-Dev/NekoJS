package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

public record NekoPreparedModule(
        String code,
        String sourceMap,
        NekoModuleMode mode,
        NekoEsmModuleAst esmAst,
        int prependedLineCount
) {
    public NekoPreparedModule {
        if (code == null) {
            code = "";
        }
        if (mode == null) {
            mode = NekoModuleMode.COMMONJS;
        }
        prependedLineCount = Math.max(0, prependedLineCount);
    }

    public static NekoPreparedModule commonJs(String code, String sourceMap) {
        return new NekoPreparedModule(code, sourceMap, NekoModuleMode.COMMONJS, null, 0);
    }

    public static NekoPreparedModule esm(String code, String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule(code, sourceMap, NekoModuleMode.ESM, ast, 0);
    }
}
