package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class NekoBabelEsmToCjsTransformer implements NekoEsmToCjsTransformer, AutoCloseable {
    private static final String TRANSFORMER_RESOURCE = "nekojs/transformer/esm-to-cjs-bundle.min.js";
    private static final String TRANSFORMER_GLOBALS = """
            globalThis.process = { env: { BABEL_8_BREAKING: 'false' } };
            """;

    private final Context context;
    private final Value transformFunction;

    public NekoBabelEsmToCjsTransformer() {
        this.context = Context.newBuilder("js")
                .engine(NekoSharedEngine.get())
                .allowHostAccess(NekoSharedHostAccess.get())
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .build();
        context.eval("js", TRANSFORMER_GLOBALS);
        evalResource(TRANSFORMER_RESOURCE);
        this.transformFunction = context.getBindings("js").getMember("__nekoTransformEsmToCjs");
        if (transformFunction == null || !transformFunction.canExecute()) {
            throw new IllegalStateException("Failed to install NekoJS Babel ESM transformer");
        }
    }

    @Override
    public synchronized NekoModuleTransformResult transform(Path file, String code, String inputSourceMap) {
        Value result = transformFunction.execute(file.toString().replace('\\', '/'), code, inputSourceMap);
        String transformedCode = result.getMember("code").asString();
        Value sourceMapValue = result.getMember("sourceMap");
        String sourceMap = sourceMapValue == null || sourceMapValue.isNull() ? null : sourceMapValue.asString();
        return new NekoModuleTransformResult(transformedCode, sourceMap, NekoModuleMode.ESM);
    }

    @Override
    public void close() {
        context.close(true);
    }

    private void evalResource(String path) {
        String source = readResource(path);
        try {
            context.eval(Source.newBuilder("js", source, path).build());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to evaluate transformer resource: " + path, e);
        }
    }

    private static String readResource(String path) {
        ClassLoader loader = NekoBabelEsmToCjsTransformer.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing transformer resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read transformer resource: " + path, e);
        }
    }
}
