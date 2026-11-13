package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

public final class NekoNodeModuleInstaller {
    private static final String RESOURCE_ROOT = "nekojs/node/";
    private static final String MANIFEST = RESOURCE_ROOT + "modules.list";

    private NekoNodeModuleInstaller() {}

    /**
     * Production assembly entry point. The module host and the assembly side share the same
     * runtime-owned prepared cache instance, which the caller injects.
     */
    public static NekoNodeRuntime install(Context context, ScriptType scriptType, NekoModuleResolver resolver, NekoJSPaths paths, ErrorTracker errorTracker, SandboxConfig sandboxConfig, NekoModulePipelineCache preparationCache) {
        NekoScriptModuleLoaderHost moduleLoaderHost = null;
        NekoNodeRuntime runtime = null;
        try {
            moduleLoaderHost = new NekoScriptModuleLoaderHost(context, resolver, preparationCache);
            runtime = new NekoNodeRuntime(scriptType, moduleLoaderHost, errorTracker, sandboxConfig);
            context.getBindings("js").putMember("__nekoNodeRuntime", runtime);
            context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", moduleLoaderHost);
            Map<String, String> pluginModules = pluginModules();
            // Strict CJS require may enter the special resolver only for ids registered by a
            // plugin. The complete allow-list is installed before any plugin module evaluates.
            moduleLoaderHost.registerSpecialModules(pluginModules.keySet());
            // The manifest's modules/timers.ts registers the global timers. The manifest is loaded
            // synchronously before any user script runs, so no simplified implementation that drops
            // extra arguments needs to be installed ahead of it
            loadManifest(context, preparationCache);
            loadPluginModules(context, pluginModules);
            return runtime;
        } catch (Throwable failure) {
            if (runtime != null) {
                try {
                    runtime.close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            } else if (moduleLoaderHost != null) {
                moduleLoaderHost.close();
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to install NekoJS Node runtime", failure);
        }
    }

    private static void loadManifest(Context context, NekoModulePipelineCache preparationCache) {
        String manifest = readResource(MANIFEST);
        for (String line : manifest.split("\\R")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String resourcePath = RESOURCE_ROOT + entry;
            Source prepared = preparationCache.prepareBuiltinSource(resourcePath, () -> {
                String source = readResource(resourcePath);
                String js = entry.endsWith(".ts")
                        ? NekoTypeScriptCompiler.eraseTypescript(Path.of(resourcePath), source)
                        : source;
                try {
                    return Source.newBuilder("js", js, resourcePath).build();
                } catch (IOException failure) {
                    throw new IllegalStateException(
                            "Failed to prepare NekoJS Node module resource: " + resourcePath, failure);
                }
            });
            context.eval(prepared);
        }
    }

    /**
     * Loads the JavaScript modules a plugin registered through {@code registerNodeModules}:
     * evaluates each with a CommonJS wrapper (injecting {@code module}/{@code exports}/{@code require}),
     * then registers it in the built-in module table through {@code __nekoNodeDefine} so
     * {@code require('moduleId')} resolves to {@code module.exports}.
     */
    private static Map<String, String> pluginModules() {
        NekoPluginRuntime runtime;
        try {
            runtime = NekoPluginRuntime.current();
        } catch (IllegalStateException ignored) {
            return Map.of(); // The runtime has not bootstrapped (a standalone install, for example), so there are no plugin modules.
        }
        Map<String, String> modules = runtime.nodeModules();
        return modules == null ? Map.of() : modules;
    }

    private static void loadPluginModules(Context context, Map<String, String> modules) {
        if (modules.isEmpty()) return;
        for (Map.Entry<String, String> entry : modules.entrySet()) {
            String id = entry.getKey();
            if (id.startsWith("nekojs/api/")) {
                throw new IllegalArgumentException("[NEKO-4057] Plugin Node module uses a reserved API route: " + id);
            }
            String source = entry.getValue();
            try {
                StringBuilder js = new StringBuilder();
                js.append("(function(){const module={exports:{}},exports=module.exports,require=globalThis.require;\n");
                js.append(source).append('\n');
                js.append("globalThis.__nekoNodeDefine(").append(jsString(id)).append(", module.exports);})();");
                context.eval(Source.newBuilder("js", js.toString(), "nekojs/node/plugin/" + id).build());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load plugin node module '" + id + "'", e);
            }
        }
    }

    /**
     * Renders a single-quoted JS string literal, escaping backslash, quote and newline, so a moduleId
     * can be embedded in a wrapper safely.
     */
    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.append("'").toString();
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
