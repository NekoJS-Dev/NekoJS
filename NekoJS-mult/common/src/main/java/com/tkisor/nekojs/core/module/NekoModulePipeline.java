package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompileOutput;
import com.tkisor.nekojs.core.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.EventCallbackSourceValidator;
import com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.module.cjs.CjsStaticAnalyzer;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 实例化模块编译管线（Script Preparation）：构造器接收 {@link NekoCompilationPipeline}、
 * {@link ScriptCompilerRegistry}、{@link SandboxConfig}。根据 ESM/CJS 模式和语言插件编译模块。
 *
 * <p>票据 11（W3）显式注入形态：调用者经构造器传入全部依赖；本类不再持有任何 process-wide
 * static 状态——历史 {@code LEGACY_INSTANCE / bindLegacyInstance / legacyInstance /
 * legacyPrepare / SHARED_COMPILATION_PIPELINE} 已随本票删除（05 总账 A7 的删除条件即
 * “W3 显式注入 pipeline/cache 后删除”；替代 behavior 与 trace 见
 * {@code NekoModulePipelinePrepareTest} / {@code NekoModulePipelineCacheStampTest}，
 * 调用者证据见 {@code ModulePipelineIsolationTest} 的零 static 调用断言）。
 * 需要实例的调用方一律经 {@code NekoRuntimeRoot} 持有的
 * {@link NekoModulePipelineCache}（runtime owner 生命周期）取得，不再有“无实例也能跑”的
 * 静默 fallback。
 *
 * <p>输出的 {@link NekoPreparedModule} 携带 language id、module mode、可执行 code、
 * 编译器实际产生或保守 fallback 生成的 source map（sourceURL 仍可作为执行器 fallback）、
 * 原始诊断位置（source path）与稳定 cache key，且不可变。
 *
 * <p>本类不创建 Graal Context、不决定 HostAccess、不读 Minecraft/loader（见
 * {@code ModulePipelineIsolationTest}）。
 */
public final class NekoModulePipeline {
    private final NekoCompilationPipeline compilationPipeline;
    private final ScriptCompilerRegistry compilers;
    private final SandboxConfig config;

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers, SandboxConfig config) {
        this.compilationPipeline = Objects.requireNonNull(compilationPipeline, "compilationPipeline");
        this.compilers = Objects.requireNonNull(compilers, "compilers");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * 纯身份解析：path/extension → source path + requested mode + language id。
     * 不编译、不读盘、不触碰全局状态。
     */
    public NekoModuleIdentity identify(Path file) {
        return captureBinding(file).identity();
    }

    public NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
        LanguageBinding binding = captureBindingChecked(file);
        return prepareWithBinding(file, rawSource == null ? "" : rawSource, binding);
    }

    /**
     * Cache-owned preparation seam: the binding captured for the source stamp drives the whole
     * prepare call, and the registry revision is checked before and after compilation.
     */
    NekoPreparedModule prepareCaptured(Path file, String rawSource, NekoTrustApprovedSource approval,
                                       LanguageBinding binding) throws Exception {
        NekoModuleIdentity identity = binding.identity();
        if (approval == null || !approval.covers(file)) {
            throw NekoModuleError.denied(NekoModuleError.displayPath(file), identity.languageId(),
                    identity.requestedMode(), approval, "preparation requires a trust-approved source");
        }
        return prepareWithBinding(file, rawSource == null ? "" : rawSource, binding);
    }

    private NekoPreparedModule prepareWithBinding(Path file, String rawSource, LanguageBinding binding) throws Exception {
        NekoModuleIdentity identity = binding.identity();
        try {
            return prepareStages(file, rawSource, binding);
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (Exception failure) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), identity.languageId(),
                    identity.requestedMode(), NekoModuleError.rootMessage(failure), failure);
        }
    }

    /**
     * 带 trust 凭证的准备门：本地受信与远端显式授权走完全相同的准备阶段，trust 只决定
     * 授权结果。凭证缺失/不覆盖该文件时抛授权拒绝（stage 仍为 PREPARE、owner 为 Pack Trust，
     * 且携带 language/mode/source——拒绝不隐藏语言边界）。
     */
    public NekoPreparedModule prepare(Path file, String rawSource, NekoTrustApprovedSource approval) throws Exception {
        LanguageBinding binding = captureBindingChecked(file);
        return prepareCaptured(file, rawSource, approval, binding);
    }

    private NekoPreparedModule prepareStages(Path file, String rawSource, LanguageBinding binding) throws Exception {
        ensureBindingCurrent(file, binding);
        String extension = extension(file);
        // 加载时静态校验：扫描脚本对全局绑定（Utils/Platform/Items 等）的成员访问，
        // 访问不存在的成员时报错到游戏内错误面板。不阻止编译/执行。
        // JS 族（原始源即 JS）在编译前对原始源跑；转译语言（.py/.ts/.tsx…）的原始源不是
        // JS（# 注释、类型注解、def/class 会被 JS-only 的 ValParser 碎成伪调用 → 'Unknown
        // identifier' 系统性误报），改为在编译后对产物 JS 跑——那才是运行时真正执行的代码。
        if (config.scriptMemberValidation() && rawPreflightApplies(extension)) {
            GlobalBindingMemberValidator.validate(file, rawSource);
            EventCallbackSourceValidator.validate(file, rawSource);
        }
        NekoPreparedModule prepared = prepareModule(file, rawSource, extension, binding);
        if (config.scriptMemberValidation() && !rawPreflightApplies(extension)) {
            GlobalBindingMemberValidator.validate(file, prepared.code());
            EventCallbackSourceValidator.validate(file, prepared.code());
        }
        ensureBindingCurrent(file, binding);
        return prepared;
    }

    /** 原始源就是 JS、可直接跑 JS-only 预检的扩展名。 */
    static boolean rawPreflightApplies(String extension) {
        return ".js".equals(extension) || ".mjs".equals(extension) || ".cjs".equals(extension) || ".jsx".equals(extension);
    }

    private NekoPreparedModule prepareModule(Path file, String rawSource, String extension,
                                              LanguageBinding binding) throws Exception {
        NekoModuleMode requestedMode = binding.identity().requestedMode();
        NekoLanguagePlugin language = binding.plugin();
        String languageId = binding.identity().languageId();
        String sourcePath = NekoModuleError.displayPath(file);

        if (!config.enableEsmAuthoring() || requestedMode == NekoModuleMode.COMMONJS) {
            if (language instanceof NekoLegacyLanguagePlugin legacyLanguage) {
                ScriptCompileResult compiled = legacyLanguage.compiler().compileDetailed(file, rawSource);
                // CJS 静态分析必须跑在编译产物上：require/module.exports 由转译生成，原始源里不存在
                return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(),
                        usableMap(file, rawSource, compiled.code(), compiled.sourceMap()),
                        CjsStaticAnalyzer.analyze(compiled.code()));
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                return NekoPreparedModule.commonJs(languageId, sourcePath, rawSource,
                        NekoSourceMapBuilder.identity(file, rawSource, rawSource),
                        CjsStaticAnalyzer.analyze(rawSource));
            }
            NekoCompileOutput compiled = compilationPipeline.compile(
                file, rawSource, extension, language, config.jsxAutomaticRuntime());
            return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(),
                    usableMap(file, rawSource, compiled.code(), compiled.program().sourceMap()),
                    CjsStaticAnalyzer.analyze(compiled.code()));
        }

        NekoCompileOutput compiled = compilationPipeline.compile(
            file, rawSource, extension, language, config.jsxAutomaticRuntime());
        return prepareModule(file, rawSource, compiled, languageId, sourcePath);
    }

    private NekoPreparedModule prepareModule(Path file, String rawSource, NekoCompileOutput compiled,
                                             String languageId, String sourcePath) {
        NekoIRProgram ir = compiled.program();
        String map = usableMap(file, rawSource, compiled.code(), compiled.program().sourceMap());
        if (ir.requestedMode() == NekoModuleMode.AUTO && !ir.module()) {
            return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(), map,
                    CjsStaticAnalyzer.analyze(compiled.code()));
        }
        NekoEsmModuleAst ast = compiled.esmAst();
        if (ast == null) {
            ast = new NekoEsmParser(null, compiled.code()).parse();
        }
        return NekoPreparedModule.esm(languageId, sourcePath, compiled.code(), map, ast);
    }

    /** Use the compiler map when present; otherwise publish a non-null conservative fallback map. */
    private static String usableMap(Path file, String authoredSource, String generatedSource, String sourceMap) {
        if (sourceMap != null && !sourceMap.isBlank()) {
            return sourceMap;
        }
        return NekoSourceMapBuilder.identity(file, authoredSource, generatedSource);
    }

    LanguageBinding captureBinding(Path file) {
        String extension = extension(file);
        NekoModuleMode requestedMode = NekoModuleMode.fromExtension(extension);
        NekoLanguagePlugin plugin = languagePlugin(file, extension);
        return new LanguageBinding(new NekoModuleIdentity(plugin.id(), requestedMode), plugin, compilers.revision());
    }

    private NekoLanguagePlugin languagePlugin(Path file, String extension) {
        NekoScriptLanguage language = compilers.getLanguage(extension);
        if (language != null) {
            if (language.plugin() != null) {
                return language.plugin();
            }
            if (language.compiler() != null) {
                return new NekoLegacyLanguagePlugin(language.id(), language.extensions(), language.compiler());
            }
        }
        IScriptCompiler compiler = compilers.getCompiler(extension);
        if (compiler != null) {
            String id = "legacy:" + extension.substring(1);
            return new NekoLegacyLanguagePlugin(id, Set.of(extension), compiler);
        }
        if (!ScriptCompilerRegistry.isNativeScriptExtension(extension)) {
            throw new IllegalArgumentException("No script compiler registered for " + extension + " module: " + file);
        }
        return NekoJavaScriptLanguagePlugin.INSTANCE;
    }

    private LanguageBinding captureBindingChecked(Path file) throws NekoModuleError {
        try {
            return captureBinding(file);
        } catch (Exception failure) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), "unknown",
                    NekoModuleMode.AUTO, NekoModuleError.rootMessage(failure), failure);
        }
    }

    private void ensureBindingCurrent(Path file, LanguageBinding binding) throws NekoModuleError {
        if (compilers.revision() != binding.registryRevision()) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), binding.identity().languageId(),
                    binding.identity().requestedMode(), "compiler registry changed during preparation", null);
        }
    }

    private String extension(Path file) {
        if (file == null || file.getFileName() == null) {
            return "";
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    record LanguageBinding(NekoModuleIdentity identity, NekoLanguagePlugin plugin, long registryRevision) {}

}
