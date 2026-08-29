package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.cjs.CjsModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

public record NekoPreparedModule(
        String code,
        String sourceMap,
        NekoModuleMode mode,
        NekoEsmModuleAst esmAst,
        CjsModuleRecord cjsRecord,
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
        return commonJs(code, sourceMap, CjsModuleRecord.EMPTY);
    }

    /** CJS 模块：附带静态分析结果（依赖/导出形状，见 {@link CjsStaticAnalyzer}）。 */
    public static NekoPreparedModule commonJs(String code, String sourceMap, CjsModuleRecord cjsRecord) {
        return new NekoPreparedModule(code, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord, 0);
    }

    public static NekoPreparedModule esm(String code, String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule(code, sourceMap, NekoModuleMode.ESM, ast, null, 0);
    }
}
