package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.cjs.CjsModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;


/**
 * An immutable prepared module: the output of script preparation and the input to module
 * resolution/cache and to the script execution environment.
 *
 * <p>Callers can observe the language id, module mode, code/IR, source map, original diagnostic
 * location ({@link #sourcePath()}) and stable cache key ({@link #cacheKey()}). The type stays
 * immutable: it is a record, so the class and all its components are final, and the dependency
 * list of {@link CjsModuleRecord} is copied into an immutable {@link java.util.List} in the
 * constructor. Resolution/cache code that wants to "modify" a module can only build a new
 * instance, whose {@link #cacheKey()} is necessarily different because the key covers
 * path/language/mode/code/sourceMap. Changing the old instance is impossible at compile time
 * (final components have no setter); reflection on a final field throws.
 *
 * <p>The stable cache key ({@link #stableCacheKey}) is {@code SHA-256("nekojs-prepared/v2" +
 * sourcePath + languageId + mode + code + sourceMap)}. The path is part of the module identity,
 * so two modules with the same content at different paths do not collide in later key-based
 * caches.
 */
public record NekoPreparedModule(
        /** Language id, such as {@code javascript}, {@code typescript}, {@code python} or {@code legacy:<ext>}. */
        String languageId,
        /** Source path used for preparation, the origin for diagnostics; null for synthetic modules. */
        String sourcePath,
        String code,
        String sourceMap,
        NekoModuleMode mode,
        NekoEsmModuleAst esmAst,
        CjsModuleRecord cjsRecord,
        int prependedLineCount,
        /** Stable cache key (see the class javadoc); the constructor computes it when left empty. */
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
     * Stable cache key derived from source path, language id, mode, code and sourceMap. A change to
     * any one input produces a different key.
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

    /**
     * A CommonJS module with its static analysis result: dependencies and export shape,
     * see {@link CjsStaticAnalyzer}.
     */
    public static NekoPreparedModule commonJs(String code, String sourceMap, CjsModuleRecord cjsRecord) {
        return new NekoPreparedModule("unknown", null, code, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord, 0, null);
    }

    /**
     * CommonJS module with an explicit language and source, as the pipeline produces. The three-arg
     * factory defaults to language=unknown and a null source.
     */
    static NekoPreparedModule commonJs(String languageId, String sourcePath, String code,
                                       String sourceMap, CjsModuleRecord cjsRecord) {
        return new NekoPreparedModule(languageId, sourcePath, code, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord, 0, null);
    }

    public static NekoPreparedModule esm(String code, String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule("unknown", null, code, sourceMap, NekoModuleMode.ESM, ast, null, 0, null);
    }

    /** ESM module with an explicit language and source, as the pipeline produces. */
    static NekoPreparedModule esm(String languageId, String sourcePath, String code,
                                  String sourceMap, NekoEsmModuleAst ast) {
        return new NekoPreparedModule(languageId, sourcePath, code, sourceMap, NekoModuleMode.ESM, ast, null, 0, null);
    }
}
