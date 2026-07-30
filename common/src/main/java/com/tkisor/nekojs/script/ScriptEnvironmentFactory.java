package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentKeyFactory;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.api.ApiFacadeProxy;
import com.tkisor.nekojs.core.api.ApiGuestErrorFactory;
import com.tkisor.nekojs.js.DelegatingBinding;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
        Map<String, ScriptBindingSchema.BindingMembers> bindingSchema = new HashMap<>();
        environmentBindings.forEach((name, binding) -> {
            Object obj = binding.value();
            bindingSchema.put(name, resolveMembers(binding));
            if (obj instanceof Class<?>) {
                Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                bindings.putMember(name, javaType);
            } else {
                bindings.putMember(name, obj);
            }
        });

        bindManagedGlobals(bindings, scriptType, bindingSchema, ApiGuestErrorFactory.create(context));

        ScriptBindingSchema.register(scriptType, bindingSchema);

        installJavaClassLoadTelemetry(context, scriptType);

        return new Environment(context, nodeRuntime);
    }

    private void bindManagedGlobals(Value bindings, ScriptType scriptType,
                                    Map<String, ScriptBindingSchema.BindingMembers> bindingSchema,
                                    ApiGuestErrorFactory guestErrorFactory) {
        EnvironmentKey key = EnvironmentKeyFactory.current(scriptType);
        ApiRuntimeView view = pluginRuntime.apiRuntime(key);
        if (view == null) return;
        ApiEnvironmentSnapshot snapshot = view.environmentSnapshot();
        if (snapshot == null) return;
        ApiSurfaceSnapshot surface = snapshot.surfaceSnapshot();
        if (surface == null) return;

        Map<String, com.tkisor.nekojs.api.surface.ApiSymbol> globals = new HashMap<>();
        surface.symbols().forEach(s -> {
            if (s.id().kind().equals("global")) {
                globals.put(s.id().qualifiedName(), s);
            }
        });

        for (var entry : globals.entrySet()) {
            String name = entry.getKey();
            if (bindingSchema.containsKey(name)) continue;
            ApiSymbolId globalId = new ApiSymbolId("global", name);
            Object impl = findImplementation(globalId);
            ApiFacadeProxy proxy = ApiFacadeProxy.global(view, globalId, impl, guestErrorFactory);
            bindings.putMember(name, proxy);
            bindingSchema.put(name, ScriptBindingSchema.fromSurface(surface, globalId));
        }
    }

    private Object findImplementation(ApiSymbolId globalId) {
        return pluginRuntime.managedApiImplementation(globalId);
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

    private static ScriptBindingSchema.BindingMembers resolveMembers(com.tkisor.nekojs.api.data.Binding binding) {
        Set<String> members = new LinkedHashSet<>();
        Object value = binding.value();
        if (value instanceof DelegatingBinding db) {
            members.addAll(db.extensions());
            members.addAll(JavaMemberIndex.allMembersOf(db.targetClass()));
        } else {
            members.addAll(JavaMemberIndex.allMembersOf(binding.valueType()));
        }
        return new ScriptBindingSchema.BindingMembers(members);
    }

    public record Environment(Context context, com.tkisor.nekojs.core.node.NekoNodeRuntime nodeRuntime) {}
}
