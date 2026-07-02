package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoModuleMode;
import com.tkisor.nekojs.api.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 实例化模块编译管线：构造器接收 {@link NekoCompilationPipeline}、{@link ScriptCompilerRegistry}、
 * {@link SandboxConfig}。根据 ESM/CJS 模式和语言插件编译模块。
 */
public final class NekoModulePipeline {
    private static final NekoCompilationPipeline SHARED_COMPILATION_PIPELINE = new NekoCompilationPipeline();
    private static volatile NekoModulePipeline LEGACY_INSTANCE;

    private final NekoCompilationPipeline compilationPipeline;
    private final ScriptCompilerRegistry compilers;
    private final SandboxConfig config;

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers, SandboxConfig config) {
        this.compilationPipeline = compilationPipeline;
        this.compilers = compilers;
        this.config = config;
    }

    public static void bindLegacyInstance(NekoModulePipeline pipeline) {
        LEGACY_INSTANCE = pipeline;
    }

    public static NekoModulePipeline legacyInstance() {
        return LEGACY_INSTANCE;
    }

    public NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
        // 加载时静态校验：扫描脚本对全局绑定（Utils/Platform/Items 等）的成员访问，
        // 访问不存在的成员时报错到游戏内错误面板。不阻止编译/执行。
        GlobalBindingMemberValidator.validate(file, rawSource);
        String extension = extension(file);
        NekoModuleMode requestedMode = NekoModuleMode.fromExtension(extension);
        NekoLanguagePlugin language = languagePlugin(file, extension);

        if (!config.enableEsmAuthoring() || requestedMode == NekoModuleMode.COMMONJS) {
            if (language instanceof NekoLegacyLanguagePlugin legacyLanguage) {
                ScriptCompileResult compiled = legacyLanguage.compiler().compileDetailed(file, rawSource);
                return NekoPreparedModule.commonJs(compiled.code(), compiled.sourceMap());
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                return NekoPreparedModule.commonJs(rawSource, null);
            }
            NekoCompileOutput compiled = compilationPipeline.compile(file, rawSource, extension, language);
            return NekoPreparedModule.commonJs(compiled.code(), compiled.program().sourceMap());
        }

        NekoCompileOutput compiled = compilationPipeline.compile(file, rawSource, extension, language);
        return prepareModule(compiled);
    }

    private NekoPreparedModule prepareModule(NekoCompileOutput compiled) {
        NekoIRProgram ir = compiled.program();
        if (ir.requestedMode() == NekoModuleMode.AUTO && !ir.module()) {
            return NekoPreparedModule.commonJs(compiled.code(), compiled.program().sourceMap());
        }
        NekoEsmModuleAst ast = compiled.esmAst();
        if (ast == null) {
            ast = new NekoEsmParser(null, compiled.code()).parse();
        }
        return NekoPreparedModule.esm(compiled.code(), compiled.program().sourceMap(), ast);
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
            return new NekoLegacyLanguagePlugin("legacy:" + extension.substring(1), Set.of(extension), compiler);
        }
        if (!ScriptCompilerRegistry.isNativeScriptExtension(extension)) {
            throw new IllegalArgumentException("No script compiler registered for " + extension + " module: " + file);
        }
        return NekoJavaScriptLanguagePlugin.INSTANCE;
    }

    private String extension(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    /* ================= Legacy static facade ================= */

    public static NekoPreparedModule legacyPrepare(Path file, String rawSource) throws Exception {
        NekoModulePipeline instance = LEGACY_INSTANCE;
        if (instance != null) {
            return instance.prepare(file, rawSource);
        }
        return legacyPrepareFallback(file, rawSource);
    }

    private static NekoPreparedModule legacyPrepareFallback(Path file, String rawSource) throws Exception {
        return new NekoModulePipeline(SHARED_COMPILATION_PIPELINE, ScriptCompilerRegistry.current(), SandboxConfig.defaultConfig())
                .prepare(file, rawSource);
    }
}
