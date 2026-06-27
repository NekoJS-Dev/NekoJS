package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.script.context.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

/**
 * 脚本环境工厂：接管 Context/Node/bindings/event/telemetry 初始化。
 *
 * <p>从 {@link ScriptManager} 的 {@code getOrCreateContext} + {@code installJavaClassLoadTelemetry}
 * 下沉而来。{@code ScriptManager} 保留 discover/load/reload/close 顶层生命周期协调，
 * 不直接承担 Context 初始化细节。
 *
 * <p>{@code create(ScriptType)} 返回 {@link Environment}，包含 {@link Context}、
 * {@link com.tkisor.nekojs.core.node.NekoNodeRuntime} 和 close/unbind 语义。
 * Context 创建后在这里绑定 {@link ScriptContextRegistry}。
 */
public final class ScriptEnvironmentFactory {
    private final ScriptEventBridge eventBridge;
    private final IPluginRuntime pluginRuntime;
    private final NekoSandboxFactory sandboxFactory;

    public ScriptEnvironmentFactory(ScriptEventBridge eventBridge, IPluginRuntime pluginRuntime, NekoSandboxFactory sandboxFactory) {
        this.eventBridge = eventBridge;
        this.pluginRuntime = pluginRuntime;
        this.sandboxFactory = sandboxFactory;
    }

    public Environment create(ScriptType scriptType) {
        NekoSandboxFactory.Sandbox sandbox = sandboxFactory.build(scriptType);
        Context context = sandbox.context();
        var nodeRuntime = sandbox.nodeRuntime();

        context.getBindings("js").putMember("__nekoCurrentScriptId", null);

        var bindings = context.getBindings("js");
        eventBridge.bindEvents(bindings, scriptType);

        var environmentBindings = pluginRuntime.bindings(scriptType);
        environmentBindings.forEach((name, binding) -> {
            Object obj = binding.value();
            if (obj instanceof Class<?>) {
                Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                bindings.putMember(name, javaType);
            } else {
                bindings.putMember(name, obj);
            }
        });

        installJavaClassLoadTelemetry(context, scriptType);

        return new Environment(context, nodeRuntime);
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

    public record Environment(Context context, com.tkisor.nekojs.core.node.NekoNodeRuntime nodeRuntime) {}
}
