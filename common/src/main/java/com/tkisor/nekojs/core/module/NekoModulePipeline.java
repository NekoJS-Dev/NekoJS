package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

public final class NekoModulePipeline {
    private static final String REQUIRE_PATCH = minifySingleLine(readResource("nekojs/node/internal/require-patch.js")) + "\n";
    private static final Object ESM_TRANSFORMER_LOCK = new Object();
    private static volatile NekoEsmToCjsTransformer esmToCjsTransformer;
    private static volatile boolean esmTransformerDisabled;

    private NekoModulePipeline() {}

    public static void setEsmToCjsTransformer(NekoEsmToCjsTransformer transformer) {
        synchronized (ESM_TRANSFORMER_LOCK) {
            esmToCjsTransformer = transformer;
            esmTransformerDisabled = transformer == null;
        }
    }

    public static NekoModuleTransformResult transform(Path file, String rawSource) throws Exception {
        String extension = extension(file);
        ScriptCompileResult compiled = compileLanguage(file, rawSource, extension);
        NekoModuleMode requestedMode = requestedMode(extension);
        NekoModuleTransformResult moduleResult = transformModule(file, compiled.code(), compiled.sourceMap(), requestedMode);
        return new NekoModuleTransformResult(REQUIRE_PATCH + moduleResult.code(), moduleResult.sourceMap(), moduleResult.mode(), 1);
    }

    private static ScriptCompileResult compileLanguage(Path file, String rawSource, String extension) throws Exception {
        IScriptCompiler compiler = ScriptCompilerRegistry.getCompiler(extension);
        if (compiler == null) {
            return ScriptCompileResult.codeOnly(rawSource);
        }
        return compiler.compileDetailed(file, rawSource);
    }

    private static NekoModuleTransformResult transformModule(Path file, String code, String sourceMap, NekoModuleMode requestedMode) throws Exception {
        if (requestedMode == NekoModuleMode.COMMONJS) {
            return new NekoModuleTransformResult(code, sourceMap, NekoModuleMode.COMMONJS);
        }

        if (requestedMode == NekoModuleMode.AUTO && !looksLikeEsm(code)) {
            return new NekoModuleTransformResult(code, sourceMap, NekoModuleMode.COMMONJS);
        }

        NekoEsmToCjsTransformer transformer = esmTransformer(file);
        return transformer.transform(file, code, sourceMap);
    }

    private static NekoEsmToCjsTransformer esmTransformer(Path file) throws IOException {
        NekoEsmToCjsTransformer transformer = esmToCjsTransformer;
        if (transformer != null) {
            return transformer;
        }
        if (esmTransformerDisabled) {
            throw new IOException("ESM syntax is not supported: " + file + ". The NekoJS ESM transformer failed to initialize earlier.");
        }
        synchronized (ESM_TRANSFORMER_LOCK) {
            transformer = esmToCjsTransformer;
            if (transformer != null) {
                return transformer;
            }
            if (esmTransformerDisabled) {
                throw new IOException("ESM syntax is not supported: " + file + ". The NekoJS ESM transformer failed to initialize earlier.");
            }
            try {
                transformer = new NekoBabelEsmToCjsTransformer();
                esmToCjsTransformer = transformer;
                return transformer;
            } catch (RuntimeException e) {
                esmTransformerDisabled = true;
                throw new IOException("Failed to initialize NekoJS ESM transformer for " + file, e);
            }
        }
    }

    private static NekoModuleMode requestedMode(String extension) {
        return switch (extension) {
            case ".mjs" -> NekoModuleMode.ESM;
            case ".cjs" -> NekoModuleMode.COMMONJS;
            default -> NekoModuleMode.AUTO;
        };
    }

    private static boolean looksLikeEsm(String code) {
        return code.contains("import ") || code.contains("export ");
    }

    private static String extension(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String minifySingleLine(String source) {
        return source.replace("\r", "").replace("\n", " ");
    }

    private static String readResource(String path) {
        ClassLoader loader = NekoModulePipeline.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing NekoJS module resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read NekoJS module resource: " + path, e);
        }
    }
}
