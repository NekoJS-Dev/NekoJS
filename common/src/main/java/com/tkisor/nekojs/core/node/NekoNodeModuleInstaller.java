package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
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

    public static NekoNodeRuntime install(Context context, ScriptType scriptType) {
        return install(context, scriptType, new NekoModuleResolver(), NekoJSPaths.get(), new com.tkisor.nekojs.core.error.DefaultErrorTracker(com.tkisor.nekojs.core.config.SandboxConfig.defaultConfig()), com.tkisor.nekojs.core.config.SandboxConfig.defaultConfig());
    }

    public static NekoNodeRuntime install(Context context, ScriptType scriptType, NekoModuleResolver resolver, NekoJSPaths paths, ErrorTracker errorTracker, SandboxConfig sandboxConfig) {
        NekoScriptModuleLoaderHost moduleLoaderHost = new NekoScriptModuleLoaderHost(context, resolver, paths);
        NekoNodeRuntime runtime = new NekoNodeRuntime(scriptType, moduleLoaderHost, errorTracker, sandboxConfig);
        context.getBindings("js").putMember("__nekoNodeRuntime", runtime);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", moduleLoaderHost);
        loadManifest(context);
        loadPluginModules(context);
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
    private static void loadPluginModules(Context context) {
        NekoPluginRuntime runtime;
        try {
            runtime = NekoPluginRuntime.current();
        } catch (IllegalStateException ignored) {
            return; // runtime 未 bootstrap（如独立 install），无插件模块
        }
        Map<String, String> modules = runtime.nodeModules();
        if (modules == null || modules.isEmpty()) {
            return;
        }
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
