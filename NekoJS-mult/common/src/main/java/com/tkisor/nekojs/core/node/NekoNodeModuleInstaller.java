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
     * 生产装配入口（W3 显式注入）：module host 与装配侧共享同一个 runtime-owned
     * prepared 缓存实例。
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
            // 全局 timers 由 manifest 的 modules/timers.ts 注册（manifest 同步加载完毕后才执行任何用户脚本，
            // 无需在 manifest 之前预装一版会丢弃额外参数的简化实现）
            loadManifest(context);
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

    private static void loadManifest(Context context) {        String manifest = readResource(MANIFEST);
        for (String line : manifest.split("\\R")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String resourcePath = RESOURCE_ROOT + entry;
            String source = readResource(resourcePath);
            // .ts 条目：擦除类型注解后求值；.js 条目原样求值
            String js = entry.endsWith(".ts")
                ? NekoTypeScriptCompiler.eraseTypescript(Path.of(resourcePath), source)
                : source;
            try {
                context.eval(Source.newBuilder("js", js, resourcePath).build());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to evaluate NekoJS Node module resource: " + resourcePath, e);
            }
        }
    }

    /**
     * 加载插件通过 {@code registerNodeModules} 注册的 JS 模块：用 CommonJS wrapper 求值
     * （注入 {@code module}/{@code exports}/{@code require}），再通过 {@code __nekoNodeDefine}
     * 注册到内置模块表，使 {@code require('moduleId')} 解析到 {@code module.exports}。
     */
    private static Map<String, String> pluginModules() {
        NekoPluginRuntime runtime;
        try {
            runtime = NekoPluginRuntime.current();
        } catch (IllegalStateException ignored) {
            return Map.of(); // runtime 未 bootstrap（如独立 install），无插件模块
        }
        Map<String, String> modules = runtime.nodeModules();
        return modules == null ? Map.of() : modules;
    }

    private static void loadPluginModules(Context context, Map<String, String> modules) {
        if (modules.isEmpty()) return;
        for (Map.Entry<String, String> entry : modules.entrySet()) {
            String id = entry.getKey();
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

    /** 单引号 JS 字符串字面量（转义 \ ' 与换行），用于把 moduleId 安全嵌入 wrapper。 */
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
