package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.cjs.CjsModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Script Preparation 的输出：<strong>不可变</strong> prepared module（工单 11 / 规格 06）。
 *
 * <p>调用者可观察语义（规格 06 Implementation Decisions 第 1 条）：
 * <ul>
 *   <li>{@link #code()} —— 可执行 code（或 IR 产物的 JS 形态）；</li>
 *   <li>{@link #languageId()} —— language id（{@code javascript} / {@code typescript} / …）；</li>
 *   <li>{@link #mode()} —— 解析后的 module mode（{@link NekoModuleMode#ESM} / {@code COMMONJS}）；</li>
 *   <li>{@link #sourceMap()} —— source map（可为 null，表示恒等映射）；</li>
 *   <li>{@link #origin()} —— 原始诊断位置（源文件路径 + 前置无源对应行数）；</li>
 *   <li>{@link #cacheKey()} —— 稳定 cache key（language id + 请求模式 + 路径 + 内容指纹）。</li>
 * </ul>
 *
 * <p><b>不可变约定：</b>record 组件本身全部是不可变类型（{@link NekoModuleCacheKey}、
 * {@link NekoModuleDiagnosticOrigin}、{@link CjsModuleRecord}、{@link NekoEsmModuleAst} 均在
 * 构造器内做 {@code List.copyOf}/{@code Set.copyOf}）。Resolution/Cache 只能消费，不能反向
 * 修改已准备的代码——需要不同输入时重新 {@link NekoModulePipeline#prepare} 得到新实例。
 *
 * <p>{@link #prependedLineCount()} 保留为派生访问器（等价 {@code origin().prependedLineCount()}），
 * 兼容既有 source map 注册调用点。
 */
public record NekoPreparedModule(
        String code,
        String sourceMap,
        NekoModuleMode mode,
        NekoEsmModuleAst esmAst,
        CjsModuleRecord cjsRecord,
        NekoModuleDiagnosticOrigin origin,
        NekoModuleCacheKey cacheKey
) {
    public NekoPreparedModule {
        if (code == null) {
            code = "";
        }
        if (mode == null) {
            mode = NekoModuleMode.COMMONJS;
        }
        cjsRecord = cjsRecord == null ? CjsModuleRecord.EMPTY : cjsRecord;
        origin = origin == null ? new NekoModuleDiagnosticOrigin(null, 0) : origin;
        cacheKey = cacheKey == null
                ? NekoModuleCacheKey.ofInline("unknown", mode, code)
                : cacheKey;
    }

    /** language id（来自 {@link NekoModuleCacheKey#languageId()}）。 */
    public String languageId() {
        return cacheKey.languageId();
    }

    /** 源文件路径（合成/内存输入为 null）。 */
    public Path sourcePath() {
        return origin.sourcePath();
    }

    /** 产物前置的无源对应行数（派生自 {@link #origin()}）。 */
    public int prependedLineCount() {
        return origin.prependedLineCount();
    }

    /** 扩展名<strong>请求</strong>的模块模式（{@link NekoModuleMode#AUTO} 表示由管线判定）。 */
    public NekoModuleMode requestedMode() {
        return cacheKey.mode();
    }

    /**
     * 完整构造：语言身份 + cache key + 诊断位置 + 代码/IR。
     * 这是 {@link NekoModulePipeline} 与 {@link NekoModulePreparation} 使用的唯一入口。
     */
    public static NekoPreparedModule of(
            NekoModuleMode mode,
            NekoModuleCacheKey cacheKey,
            Path sourcePath,
            String code,
            String sourceMap,
            NekoEsmModuleAst esmAst,
            CjsModuleRecord cjsRecord,
            int prependedLineCount
    ) {
        return new NekoPreparedModule(code, sourceMap, mode, esmAst, cjsRecord,
                new NekoModuleDiagnosticOrigin(sourcePath, prependedLineCount), cacheKey);
    }

    /** 合成/测试用 CJS 模块（无源文件、无 source map、language id 未知）。 */
    public static NekoPreparedModule commonJs(String code, String sourceMap) {
        return commonJs(code, sourceMap, CjsModuleRecord.EMPTY);
    }

    /**
     * 合成/测试用 CJS 模块；附带静态分析结果（依赖/导出形状，见 {@link CjsStaticAnalyzer}）。
     *
     * <p>生产路径请用 {@link #of}——本工厂不带 language id 与源路径，cache key 退化为 inline。
     */
    public static NekoPreparedModule commonJs(String code, String sourceMap, CjsModuleRecord cjsRecord) {
        String safeCode = code == null ? "" : code;
        return new NekoPreparedModule(safeCode, sourceMap, NekoModuleMode.COMMONJS, null, cjsRecord,
                new NekoModuleDiagnosticOrigin(null, 0),
                NekoModuleCacheKey.ofInline("unknown", NekoModuleMode.COMMONJS, safeCode));
    }

    /** 合成/测试用 ESM 模块（无源文件）。生产路径请用 {@link #of}。 */
    public static NekoPreparedModule esm(String code, String sourceMap, NekoEsmModuleAst ast) {
        String safeCode = code == null ? "" : code;
        return new NekoPreparedModule(safeCode, sourceMap, NekoModuleMode.ESM, ast, null,
                new NekoModuleDiagnosticOrigin(null, 0),
                NekoModuleCacheKey.ofInline("unknown", NekoModuleMode.ESM, safeCode));
    }

    /** 断言用：本 record 的组件类型全部不可变（供 AC2 不可变性测试引用）。 */
    public static void requireImmutableComponents(NekoPreparedModule module) {
        Objects.requireNonNull(module.code(), "code");
        Objects.requireNonNull(module.mode(), "mode");
        Objects.requireNonNull(module.origin(), "origin");
        Objects.requireNonNull(module.cacheKey(), "cacheKey");
    }
}
