package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.cjs.CjsModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;


/**
 * 不可变 prepared module：Script Preparation 的输出，Module Resolution/Cache 与
 * Script Execution Environment 的输入。
 *
 * <p>票据 11 要求调用者可观察语义包含 language id、module mode、code/IR、source map、
 * 原始诊断位置（{@link #sourcePath()}）和稳定 cache key（{@link #cacheKey()}），且保持
 * 不可变：本类型是 record（类与全部组件 final），{@link CjsModuleRecord} 的依赖表在构造时
 * 即拷贝为不可变 {@link java.util.List}。Resolution/Cache 如需“修改”只能构造新实例——
 * 新实例的 {@link #cacheKey()} 必然不同（key 覆盖 path/language/mode/code/sourceMap），
 * 篡改旧实例在编译期不可能（final 组件无 setter），反射改 final 字段会抛异常。
 *
 * <p>稳定 cache key 口径（{@link #stableCacheKey}）：{@code SHA-256("nekojs-prepared/v2" +
 * sourcePath + languageId + mode + code + sourceMap)}。路径是模块身份的一部分，避免两个
 * 路径上的同内容模块在后续 key-based cache 中发生身份碰撞。
 */
public record NekoPreparedModule(
        /** 语言 id：如 {@code javascript}、{@code typescript}、{@code python}、{@code legacy:<ext>}。 */
        String languageId,
        /** 准备来源路径（诊断原点；合成模块可为 null）。 */
        String sourcePath,
        String code,
        String sourceMap,
        NekoModuleMode mode,
        NekoEsmModuleAst esmAst,
        CjsModuleRecord cjsRecord,
        int prependedLineCount,
        /** 稳定 cache key（见类注口径；构造时若为空则按口径计算）。 */
        String cacheKey
) {
    public NekoPreparedModule {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        if (code == null) {
            code = "";
        }
        if (mode == null) {
            mode = NekoModuleMode.COMMONJS;
        }
        prependedLineCount = Math.max(0, prependedLineCount);
        if (cacheKey == null || cacheKey.isBlank()) {
            cacheKey = stableCacheKey(sourcePath, languageId, mode, code, sourceMap);
        }
    }

    /**
     * 稳定 cache key：由 source path、language id、mode、code、sourceMap 共同决定；
     * 任一输入变化即产生不同 key（篡改即变红的判定基础）。
     */
    static String stableCacheKey(String sourcePath, String languageId, NekoModuleMode mode,
                                 String code, String sourceMap) {
        String normalizedPath = sourcePath == null ? "" : sourcePath.replace('\\', '/');
        String normalizedLanguage = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        NekoModuleMode normalizedMode = mode == null ? NekoModuleMode.COMMONJS : mode;
        String normalizedCode = code == null ? "" : code;
        String normalizedMap = sourceMap == null ? "" : sourceMap;
        String material = "nekojs-prepared/v2\0" + normalizedPath + "\0" + normalizedLanguage + "\0" + normalizedMode.name()
                + "\0" + normalizedCode + "\0" + normalizedMap;
        return NekoModuleHash.sha256(material);
    }

    public static NekoPreparedModule commonJs(String code, String sourceMap) {
        return commonJs(code, sourceMap, CjsModuleRecord.EMPTY);
    }

    /** CJS 模块：附带静态分析结果（依赖/导出形状，见 {@link CjsStaticAnalyzer}）。 */
    public static NekoPreparedModule commonJs(String code, String sourceMap, CjsModuleRecord cjsRecord) {
        return new NekoPreparedModule("unknown", null, code, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord, 0, null);
    }

    /** 显式语言/来源的 CJS 模块（管线生产路径用；旧三参工厂默认 language=unknown、来源 null）。 */
    static NekoPreparedModule commonJs(String languageId, String sourcePath, String code,
                                       String sourceMap, CjsModuleRecord cjsRecord) {
        return new NekoPreparedModule(languageId, sourcePath, code, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord, 0, null);
    }

    public static NekoPreparedModule esm(String code, String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule("unknown", null, code, sourceMap, NekoModuleMode.ESM, ast, null, 0, null);
    }

    /** 显式语言/来源的 ESM 模块（管线生产路径用）。 */
    static NekoPreparedModule esm(String languageId, String sourcePath, String code,
                                  String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule(languageId, sourcePath, code, sourceMap, NekoModuleMode.ESM, ast, null, 0, null);
    }
}
