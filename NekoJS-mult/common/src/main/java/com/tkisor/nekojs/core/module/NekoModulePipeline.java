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
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.module.cjs.CjsStaticAnalyzer;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * <strong>Script Preparation</strong>（工单 11 / 规格 06）：实例化模块准备管线。
 *
 * <p>构造器接收 {@link NekoCompilationPipeline}、{@link ScriptCompilerRegistry}、
 * {@link SandboxConfig}——没有 process-wide static 实例、没有 legacy 静态门面：
 * 所有权由 runtime owner（{@code NekoRuntimeRoot} 经 {@link NekoModulePreparation}）显式持有。
 *
 * <p>职责边界（规格 06）：本类<strong>不</strong>创建 Graal Context、<strong>不</strong>决定
 * HostAccess、<strong>不</strong>读取 Minecraft/loader。它只把 source/path/extension/requested
 * mode 转成不可变的 {@link NekoPreparedModule}；Context、HostAccess、bindings 与执行关闭
 * 继续由 Script Execution Environment 负责。
 */
public final class NekoModulePipeline {

    private final NekoCompilationPipeline compilationPipeline;
    private final ScriptCompilerRegistry compilers;
    private final SandboxConfig config;

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers, SandboxConfig config) {
        this.compilationPipeline = compilationPipeline;
        this.compilers = compilers;
        this.config = config;
    }

    /**
     * 语言身份（language id + 扩展名请求的 module mode）：<strong>不需要</strong>运行编译管线即可计算，
     * 因此 {@link NekoModulePreparation} 可以在 prepare 之前用它做 cache 命中判定。
     *
     * <p>language id 取编译器注册表解析出的语言插件 id——这正是「这条扩展名归哪个语言」的事实，
     * 与 {@link #prepare} 写入 {@link NekoPreparedModule#languageId()} 的值同源，保证命中判定与
     * 实际产物一致（不做「预判 id 与实际 id 不同 → 永久 miss」的错配）。
     */
    public NekoModuleIdentity identityOf(Path file) {
        String extension = extension(file);
        return new NekoModuleIdentity(languagePlugin(file, extension).id(), NekoModuleMode.fromExtension(extension));
    }

    /**
     * 准备模块：source/path/extension/requested mode → 不可变 prepared module。
     *
     * <p>输入只有 file 与 rawSource；extension 由 path 派生，requested mode 由 extension 派生，
     * trust-approved 的判定属于调用方（Pack Trust）——本地 trusted 与远端 explicitly-authorized
     * source 复用这同一个 prepare 阶段，trust 决策只影响「是否走到这里」，不改变准备语义。
     */
    public NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
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
        NekoPreparedModule prepared = prepareModule(file, rawSource, extension);
        if (config.scriptMemberValidation() && !rawPreflightApplies(extension)) {
            GlobalBindingMemberValidator.validate(file, prepared.code());
            EventCallbackSourceValidator.validate(file, prepared.code());
        }
        return prepared;
    }

    /** 原始源就是 JS、可直接跑 JS-only 预检的扩展名。*/
    static boolean rawPreflightApplies(String extension) {
        return ".js".equals(extension) || ".mjs".equals(extension) || ".cjs".equals(extension) || ".jsx".equals(extension);
    }

    private NekoPreparedModule prepareModule(Path file, String rawSource, String extension) throws Exception {
        NekoModuleMode requestedMode = NekoModuleMode.fromExtension(extension);
        NekoLanguagePlugin language = languagePlugin(file, extension);
        NekoModuleIdentity identity = new NekoModuleIdentity(language.id(), requestedMode);
        NekoModuleCacheKey cacheKey = NekoModuleCacheKey.of(identity.languageId(), identity.mode(), file, rawSource);

        if (!config.enableEsmAuthoring() || requestedMode == NekoModuleMode.COMMONJS) {
            if (language instanceof NekoLegacyLanguagePlugin legacyLanguage) {
                ScriptCompileResult compiled = legacyLanguage.compiler().compileDetailed(file, rawSource);
                // CJS 静态分析必须跑在编译产物上：require/module.exports 由转译生成，原始源里不存在
                return NekoPreparedModule.of(NekoModuleMode.COMMONJS, cacheKey, file, compiled.code(),
                        compiled.sourceMap(), null, CjsStaticAnalyzer.analyze(compiled.code()), 0);
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                // 原生 JS 的 CJS 路径：产物就是原始源，因此 sourceMap = null 表示<strong>恒等映射</strong>
                // ——SourceMapRegistry 对 null/空 map 落 NormalizedSourceMap.empty，查表返回
                // (jsLine, jsColumn, path=null)，即行列原样透传、errorPath 回退到 displayPath。
                // 生成 source map 反而会引入一份「源到源」的冗余映射，对定位没有任何增益。
                // 该契约由 NekoModulePreparationIdentityTest.sourceMapNullMeansIdentityForNativeJs 固定。
                return NekoPreparedModule.of(NekoModuleMode.COMMONJS, cacheKey, file, rawSource,
                        null, null, CjsStaticAnalyzer.analyze(rawSource), 0);
            }
            NekoCompileOutput compiled = compilationPipeline.compile(
                file, rawSource, extension, language, config.jsxAutomaticRuntime());
            return NekoPreparedModule.of(NekoModuleMode.COMMONJS, cacheKey, file, compiled.code(),
                    compiled.program().sourceMap(), null, CjsStaticAnalyzer.analyze(compiled.code()), 0);
        }

        NekoCompileOutput compiled = compilationPipeline.compile(
            file, rawSource, extension, language, config.jsxAutomaticRuntime());
        return prepareModule(compiled, cacheKey, file);
    }

    private NekoPreparedModule prepareModule(NekoCompileOutput compiled, NekoModuleCacheKey cacheKey, Path file) {
        NekoIRProgram ir = compiled.program();
        if (ir.requestedMode() == NekoModuleMode.AUTO && !ir.module()) {
            return NekoPreparedModule.of(NekoModuleMode.COMMONJS, cacheKey, file, compiled.code(),
                    ir.sourceMap(), null, CjsStaticAnalyzer.analyze(compiled.code()), 0);
        }
        NekoEsmModuleAst ast = compiled.esmAst();
        if (ast == null) {
            ast = new NekoEsmParser(null, compiled.code()).parse();
        }
        return NekoPreparedModule.of(NekoModuleMode.ESM, cacheKey, file, compiled.code(),
                ir.sourceMap(), ast, null, 0);
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
}
