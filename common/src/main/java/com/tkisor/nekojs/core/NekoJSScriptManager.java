package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.catalog.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.NekoBindings;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NekoJS 脚本引擎核心生命周期调度器
 */
public final class NekoJSScriptManager {
    private static ScriptEventBridge eventBridge = ScriptEventBridge.EMPTY;

    private final ScriptTypedValue<Context> contexts = ScriptTypedValue.ofNullable(this::initContext);

    private final ScriptTypedValue<NekoNodeRuntime> nodeRuntimes = ScriptTypedValue.ofNullable(type -> null);

    private final ScriptTypedValue<List<ScriptContainer>> scripts = ScriptTypedValue.of(type -> new ArrayList<>());

    private final ScriptPropertyRegistry scriptPropertyRegistry = new ScriptPropertyRegistry.Impl();

    private static final Map<Context, ScriptType> CONTEXT_TYPE_MAP = Collections.synchronizedMap(new WeakHashMap<>());

    public NekoJSScriptManager() {
    }

    public static void setEventBridge(ScriptEventBridge bridge) {
        eventBridge = bridge == null ? ScriptEventBridge.EMPTY : bridge;
    }

    public static void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    public void registerScriptProperty() {
        for (var plugin : NekoJSBasePluginManager.getPlugins()) {
            plugin.registerScriptProperty(scriptPropertyRegistry);
        }
    }

    /**
     * 一次性扫描并发现所有环境类型 (STARTUP, SERVER, CLIENT) 的脚本文件
     */
    public void discoverScripts() {
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            discoverScripts(type);
        }
    }

    /**
     * 发现并准备环境，但不执行
     */
    public void discoverScripts(ScriptType type) {
        List<ScriptContainer> discovered = ScriptLocator.discover(type, scriptPropertyRegistry);
        scripts.set(type, discovered);
        type.logger().info("发现了 {} 个 {} 脚本。", discovered.size(), type.name());
    }

    /**
     * 加载并顺序执行所有脚本
     */
    public void loadScripts(ScriptType type) {
        List<ScriptContainer> scripts = this.scripts.at(type);

        Context ctx = contexts.at(type);

        for (var script : scripts) {
            script.preload();
        }

        scripts.sort((s1, s2) -> {
            int p1 = s1.properties.getOrDefault(ScriptProperty.PRIORITY);
            int p2 = s2.properties.getOrDefault(ScriptProperty.PRIORITY);

            return Integer.compare(p2, p1);
        });

        for (ScriptContainer script : scripts) {
            if (script.shouldRun()) {
                runScript(ctx, script);
            }
        }

        if (type == ScriptType.STARTUP) {
            flushReadyNodeTimers(type);
        }
    }

    private Context initContext(ScriptType type) {
        NekoSandboxBuilder.Sandbox sandbox = NekoSandboxBuilder.buildSandbox(type);
        Context ctx = sandbox.context();
        nodeRuntimes.set(type, sandbox.nodeRuntime());
        CONTEXT_TYPE_MAP.put(ctx, type);

        var bindings = ctx.getBindings("js");

        eventBridge.bindEvents(bindings, type);

        Map<String, Binding> environmentBindings = NekoBindings.getFor(type);

        environmentBindings.forEach((name, binding) -> {
            Object obj = binding.getObject();

            if (binding.isStaticClass()) {
                // 如果是静态类，利用 Java.type 包装暴露给 JS
                Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                bindings.putMember(name, javaType);
            } else {
                // 普通对象直接注入
                bindings.putMember(name, obj);
            }
        });

        installJavaClassLoadTelemetry(ctx, type);

        return ctx;
    }

    private void installJavaClassLoadTelemetry(Context ctx, ScriptType type) {
        if (!JavaClassLoadTelemetry.isEnabled()) return;

        var bindings = ctx.getBindings("js");
        bindings.putMember("__nekoJavaClassLoadTelemetry", new JavaClassLoadTelemetry());
        bindings.putMember("__nekoScriptType", type.name());
        bindings.putMember("__nekoCurrentScriptId", null);
        ctx.eval("js", """
                (function() {
                    if (Java.__nekoTypeTelemetryInstalled) return;
                    const rawType = Java.type.bind(Java);
                    Java.type = function(className) {
                        const result = rawType(className);
                        if (typeof __nekoCurrentScriptId === 'string') {
                            __nekoJavaClassLoadTelemetry.recordLoad(__nekoScriptType, __nekoCurrentScriptId, String(className));
                        }
                        return result;
                    };
                    Java.loadClass = Java.type;
                    Object.defineProperty(Java, '__nekoTypeTelemetryInstalled', { value: true, enumerable: false });
                })();
                """);
    }

    private void runScript(Context ctx, ScriptContainer script) {
        try {
            Path relativePath = NekoJSPaths.ROOT.relativize(script.path);
            String requirePath = "./" + relativePath.toString().replace("\\", "/");

            JavaClassLoadTelemetry.enter(script.type, script.id.toString());
            ctx.getBindings("js").putMember("__nekoCurrentScriptId", script.id.toString());
            ctx.eval("js", "require").execute(requirePath);

            NekoErrorTracker.clearByScriptPath(script.type, relativePath.toString().replace("\\", "/"));
            script.disabled = false;
            script.lastError = null;

        } catch (Throwable t) {
            script.disabled = true;
            script.lastError = t;

            NekoErrorTracker.record(script, t);

            if (t instanceof PolyglotException polyglotException) {
                String cleanTrace = NekoErrorTracker.getMappedStackTrace(polyglotException);

                script.type.logger().error("脚本执行失败: {}\n{}", script.id.toString(), cleanTrace);
            } else {
                script.type.logger().error("脚本内部环境崩溃: {}", script.id.toString(), t);
            }
        } finally {
            ctx.getBindings("js").putMember("__nekoCurrentScriptId", null);
            JavaClassLoadTelemetry.exit();
        }
    }

    /**
     * 重载指定类型的脚本
     */
    public void reloadScripts(ScriptType type) {
        type.logger().info("正在重载 {} 脚本...", type.name());

        resetEnvironment(type);

        discoverScripts(type);
        loadScripts(type);

        type.logger().info("{} 脚本重载完毕。", type.name());
    }

    public void runTestScripts() {
        ScriptType type = ScriptType.TEST;
        type.logger().info("正在运行 TEST 脚本...");

        resetEnvironment(type);
        discoverScripts(type);
        loadScripts(type);
        flushTestTimers();

        type.logger().info("TEST 脚本运行完毕。");
    }

    private void flushTestTimers() {
        NekoNodeRuntime runtime = nodeRuntimes.at(ScriptType.TEST);
        if (runtime == null) return;
        for (int i = 0; i < 20 && runtime.hasPendingTimers(); i++) {
            runtime.flushReadyTimers();
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        runtime.flushReadyTimers();
    }

    private void resetEnvironment(ScriptType type) {
        eventBridge.clearListeners(type);
        NekoErrorTracker.clearByType(type);

        Context oldContext = contexts.set(type, null);
        NekoNodeRuntime oldRuntime = nodeRuntimes.set(type, null);
        if (oldRuntime != null) {
            try {
                oldRuntime.close();
            } catch (Exception e) {
                type.logger().warn("关闭旧 Node runtime 时发生异常", e);
            }
        }
        if (oldContext != null) {
            try {
                oldContext.close();
            } catch (Exception e) {
                type.logger().warn("关闭旧上下文时发生异常", e);
            }
        }
    }

    public boolean hasScripts(ScriptType type) {
        List<ScriptContainer> typeScripts = scripts.at(type);
        return typeScripts != null && !typeScripts.isEmpty();
    }

    public void flushReadyNodeTimers(ScriptType type) {
        NekoNodeRuntime runtime = nodeRuntimes.at(type);
        if (runtime != null) {
            runtime.flushReadyTimers();
        }
    }

    /**
     * 从上下文获取对应的脚本类型
     * @param context 执行上下文
     * @return 对应的脚本类型
     */
    public static ScriptType getTypeFromContext(Context context) {
        return CONTEXT_TYPE_MAP.get(context);
    }
}
