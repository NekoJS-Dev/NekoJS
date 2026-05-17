package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.Locale;

public final class NekoModulePipeline {
    private NekoModulePipeline() {}

    public static NekoModuleTransformResult transform(Path file, String rawSource) throws Exception {
        NekoPreparedModule prepared = prepare(file, rawSource);
        return new NekoModuleTransformResult(prepared.code(), prepared.sourceMap(), prepared.mode(), prepared.prependedLineCount());
    }

    public static NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
        String extension = extension(file);
        ScriptCompileResult compiled = compileLanguage(file, rawSource, extension);
        NekoModuleMode requestedMode = requestedMode(extension);
        return prepareModule(file, compiled.code(), compiled.sourceMap(), requestedMode);
    }

    private static ScriptCompileResult compileLanguage(Path file, String rawSource, String extension) throws Exception {
        IScriptCompiler compiler = ScriptCompilerRegistry.current().getCompiler(extension);
        if (compiler == null) {
            if (!ScriptCompilerRegistry.isNativeScriptExtension(extension)) {
                throw new IllegalArgumentException("No script compiler registered for " + extension + " module: " + file);
            }
            return ScriptCompileResult.codeOnly(rawSource);
        }
        return compiler.compileDetailed(file, rawSource);
    }

    private static NekoPreparedModule prepareModule(Path file, String code, String sourceMap, NekoModuleMode requestedMode) throws Exception {
        if (!ClassFilter.enableEsmAuthoring || requestedMode == NekoModuleMode.COMMONJS) {
            return NekoPreparedModule.commonJs(code, sourceMap);
        }

        NekoEsmModuleAst ast = parseEsm(file, code);
        if (requestedMode == NekoModuleMode.AUTO && !ast.module()) {
            return NekoPreparedModule.commonJs(code, sourceMap);
        }
        return NekoPreparedModule.esm(code, sourceMap, ast);
    }

    private static NekoEsmModuleAst parseEsm(Path file, String code) {
        return new NekoEsmParser(file, code).parse();
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
