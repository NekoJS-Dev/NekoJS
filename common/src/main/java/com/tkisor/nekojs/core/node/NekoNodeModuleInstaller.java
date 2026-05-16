package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class NekoNodeModuleInstaller {
    private static final String RESOURCE_ROOT = "nekojs/node/";
    private static final String MANIFEST = RESOURCE_ROOT + "modules.list";

    private NekoNodeModuleInstaller() {}

    public static NekoNodeRuntime install(Context context, ScriptType scriptType) {
        NekoNodeRuntime runtime = new NekoNodeRuntime(scriptType);
        context.getBindings("js").putMember("__nekoNodeRuntime", runtime);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", new NekoScriptModuleLoaderHost(context));
        loadManifest(context);
        return runtime;
    }

    private static void loadManifest(Context context) {
        String manifest = readResource(MANIFEST);
        for (String line : manifest.split("\\R")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String resourcePath = RESOURCE_ROOT + entry;
            String source = readResource(resourcePath);
            try {
                context.eval(Source.newBuilder("js", source, resourcePath).build());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to evaluate NekoJS Node module resource: " + resourcePath, e);
            }
        }
    }

    private static String readResource(String path) {
        ClassLoader loader = NekoNodeModuleInstaller.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing NekoJS Node module resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read NekoJS Node module resource: " + path, e);
        }
    }
}
