package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class NekoModulePipeline {
    private static final NekoCompilationPipeline COMPILATION_PIPELINE = new NekoCompilationPipeline(null);

    private NekoModulePipeline() {}

    public static NekoModuleTransformResult transform(Path file, String rawSource) throws Exception {
        NekoPreparedModule prepared = prepare(file, rawSource);
        return new NekoModuleTransformResult(prepared.code(), prepared.sourceMap(), prepared.mode(), prepared.prependedLineCount());
    }

    public static NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
        String extension = extension(file);
        NekoModuleMode requestedMode = requestedMode(extension);
        NekoLanguagePlugin language = languagePlugin(file, extension);

        if (!ClassFilter.enableEsmAuthoring || requestedMode == NekoModuleMode.COMMONJS) {
            if (language instanceof NekoLegacyLanguagePlugin legacyLanguage) {
                ScriptCompileResult compiled = legacyLanguage.compiler().compileDetailed(file, rawSource);
                return NekoPreparedModule.commonJs(compiled.code(), compiled.sourceMap());
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                return NekoPreparedModule.commonJs(rawSource, null);
            }
            NekoCompileOutput compiled = COMPILATION_PIPELINE.compile(file, rawSource, extension, language);
            return NekoPreparedModule.commonJs(compiled.code(), compiled.sourceMap());
        }

        NekoCompileOutput compiled = COMPILATION_PIPELINE.compile(file, rawSource, extension, language);
        return prepareModule(compiled);
    }

    private static NekoPreparedModule prepareModule(NekoCompileOutput compiled) {
        NekoIRProgram ir = compiled.program();
        if (ir.requestedMode() == NekoModuleMode.AUTO && !ir.module()) {
            return NekoPreparedModule.commonJs(compiled.code(), compiled.sourceMap());
        }
        if (!(ir.nativeAst() instanceof NekoEsmModuleAst ast)) {
            return NekoPreparedModule.commonJs(compiled.code(), compiled.sourceMap());
        }
        return NekoPreparedModule.esm(compiled.code(), compiled.sourceMap(), ast);
    }

    private static NekoLanguagePlugin languagePlugin(Path file, String extension) {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.current();
        NekoScriptLanguage language = registry.getLanguage(extension);
        if (language != null) {
            if (language.plugin() != null) {
                return language.plugin();
            }
            if (language.compiler() != null) {
                return new NekoLegacyLanguagePlugin(language.id(), language.extensions(), language.compiler());
            }
        }
        IScriptCompiler compiler = registry.getCompiler(extension);
        if (compiler != null) {
            return new NekoLegacyLanguagePlugin("legacy:" + extension.substring(1), Set.of(extension), compiler);
        }
        if (!ScriptCompilerRegistry.isNativeScriptExtension(extension)) {
            throw new IllegalArgumentException("No script compiler registered for " + extension + " module: " + file);
        }
        return NekoJavaScriptLanguagePlugin.INSTANCE;
    }

    private static NekoModuleMode requestedMode(String extension) {
        return switch (extension) {
            case ".mjs" -> NekoModuleMode.ESM;
            case ".cjs" -> NekoModuleMode.COMMONJS;
            default -> NekoModuleMode.AUTO;
        };
    }

    private static String extension(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

}
