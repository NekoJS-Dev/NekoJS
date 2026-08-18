package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
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
import java.util.List;
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
        addEventGroupSchema(bindingSchema, pluginRuntime.eventGroups().values(), ScriptEventRegistry.groupsFor(scriptType));

        ScriptBindingSchema.register(scriptType, bindingSchema);
        // 未定义标识符检查的已知全局全集：以运行时 Context 真实可见的全局为准——
        // globalThis 全量属性名（JS 内置 + console 等引擎全局）∪ polyglot 绑定键（平台装的绑定），
        // 并补上解析器视作标识符的关键字（this/arguments/super）。单一来源都会漏：
        // console 不在绑定键里、而 guest 侧注入的绑定不一定都在 globalThis 上。
        Set<String> knownGlobals = new LinkedHashSet<>(context.getBindings("js").getMemberKeys());
        Value globalNames = context.eval("js", "Object.getOwnPropertyNames(globalThis)");
        if (globalNames.hasArrayElements()) {
            for (long i = 0; i < globalNames.getArraySize(); i++) {
                knownGlobals.add(globalNames.getArrayElement(i).asString());
            }
        }
        knownGlobals.addAll(List.of("this", "arguments", "super"));
        ScriptBindingSchema.registerGlobals(scriptType, knownGlobals);

        installJavaClassLoadTelemetry(context, scriptType);

        return new Environment(context, nodeRuntime, sandbox.outStream(), sandbox.errStream());
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

    /**
     * 事件组也是全局绑定（{@code ServerEvents}/{@code BlockEvents}/…由 eventBridge.bindEvents 绑定），
     * 必须一并进入 {@link ScriptBindingSchema}：{@code EventCallbackSourceValidator} 以
     * {@code schema.containsKey("ServerEvents")} 判定「这是事件组调用」再校验回调体，缺了这一步
     * 事件回调 preflight（如 {@code event.recipes} 拼写检查）在生产环境整体静默失效
     * （单测手工 register 过 schema，掩盖了这条断链）。组内合法成员 = 事件名（bus 名）。
     *
     * <p>ScriptEvents 自定义事件组（{@code ScriptEventGroupJS}）成员 = 已注册定义名。
     * 已存在的条目（环境绑定/managed 全局）不覆盖，自定义组用 {@code putIfAbsent}。
     */
    static void addEventGroupSchema(Map<String, ScriptBindingSchema.BindingMembers> schema,
                                     Iterable<EventGroup> groups,
                                     Map<String, Map<String, ScriptEventDefinition>> scriptEventGroups) {
        for (EventGroup group : groups) {
            schema.put(group.name(),
                    new ScriptBindingSchema.BindingMembers(Set.copyOf(group.viewBuses().keySet())));
        }
        scriptEventGroups.forEach((name, definitions) ->
                schema.putIfAbsent(name,
                        new ScriptBindingSchema.BindingMembers(Set.copyOf(definitions.keySet()))));
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
        Set<Class<?>> classes = new LinkedHashSet<>();
        Object value = binding.value();
        if (value instanceof DelegatingBinding db) {
            members.addAll(db.extensions());
            members.addAll(JavaMemberIndex.allMembersOf(db.targetClass()));
            classes.add(db.targetClass());
        }
        classes.add(binding.valueType());
        members.addAll(JavaMemberIndex.allMembersOf(binding.valueType()));
        return new ScriptBindingSchema.BindingMembers(members, classes);
    }

    /**
     * 携带 out/err {@link LoggerStream}：Graal 关闭 Context 时只 detach 用户流、不 close，
     * 由 ScriptManager 的销毁路径在 context.close() 之后补一次 close() 冲刷末行缓冲。
     */
    public record Environment(Context context, com.tkisor.nekojs.core.node.NekoNodeRuntime nodeRuntime,
                              com.tkisor.nekojs.core.log.LoggerStream outStream,
                              com.tkisor.nekojs.core.log.LoggerStream errStream) {}
}
