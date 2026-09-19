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
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.api.ApiFacadeProxy;
import com.tkisor.nekojs.core.api.ApiGuestErrorFactory;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
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
 * <p>{@code create(ScriptType, NekoModulePipelineCache)} 返回 {@link Environment}，包含 {@link Context}、
 * {@link com.tkisor.nekojs.core.node.NekoNodeRuntime} 和 close/unbind 语义。
 * Context 创建后在这里绑定 {@link ScriptContextRegistry}。
 */
public final class ScriptEnvironmentFactory {
    private final ScriptEventBridge eventBridge;
    private final IPluginRuntime pluginRuntime;
    private final NekoSandboxFactory sandboxFactory;
    /** root 拥有的 global/shared 状态域（票 10）：视图随环境安装，store 由 root 生命周期持有。 */
    private final GlobalStateStores globalStores;

    public ScriptEnvironmentFactory(ScriptEventBridge eventBridge, IPluginRuntime pluginRuntime,
                                    NekoSandboxFactory sandboxFactory, GlobalStateStores globalStores) {
        this.eventBridge = eventBridge;
        this.pluginRuntime = pluginRuntime;
        this.sandboxFactory = sandboxFactory;
        this.globalStores = globalStores;
    }

    /** 本工厂接线到的 root 状态域（ScriptManager 为每个环境创建 generation 视图）。 */
    public GlobalStateStores globalStores() {
        return globalStores;
    }

    /** 创建一个 generation 的 global/shared 视图持有者（事务=候选写集；非事务=直接提交）。 */
    public GenerationGlobals newGeneration(ScriptType scriptType, boolean transactional) {
        return globalStores.newGeneration(scriptType, transactional);
    }

    /**
     * 创建候选/active 的裸环境：sandbox Context + node runtime，<b>不含</b>任何
     * 事件组 / 插件 binding / schema 安装。
     *
     * <p>ticket 06 generation 隔离：preparation（本方法）与 binding 安装
     * （{@link #installEnvironmentBindings}）拆分为两个阶段，reload 失败结果可区分
     * PREPARATION 与 BINDING 阶段。
     */
    /** Create a bare environment against the supplied generation module session. */
    public Environment createContext(ScriptType scriptType, NekoModulePipelineCache moduleSession) {
        NekoSandboxFactory.Sandbox sandbox = sandboxFactory.build(scriptType, moduleSession);
        return environmentFrom(sandbox);
    }

    private Environment environmentFrom(NekoSandboxFactory.Sandbox sandbox) {
        Context context = sandbox.context();
        var nodeRuntime = sandbox.nodeRuntime();
        try {
            context.getBindings("js").putMember("__nekoCurrentScriptId", null);
            return Environment.bare(context, nodeRuntime, sandbox.outStream(), sandbox.errStream());
        } catch (Throwable failure) {
            try {
                if (nodeRuntime != null) nodeRuntime.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                context.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            closeStream(sandbox.outStream(), failure);
            closeStream(sandbox.errStream(), failure);
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to initialize NekoJS environment", failure);
        }
    }

    /**
     * 把事件组绑定、插件 binding、managed global、{@code global}/{@code shared} 状态视图、
     * binding schema 与 class-load telemetry 安装进给定 Context（BINDING 阶段）。
     *
     * <p>全部写入都以 {@code context.getBindings("js")} 为目标——candidate Context 与
     * active Context 各自持有自己的成员表，安装失败只影响候选环境，不触碰 active。
     *
     * <p>{@code global}/{@code shared}（票 10，shared 为工作名）：绑定值是<b>该 generation
     * 的视图</b>（{@link GenerationGlobals}），不是共享 Map 实例——候选 generation 的顶层
     * 写进候选写集，active generation 直接提交；绑定定义（名字/schema）与按 generation
     * 创建的视图由此分开。{@code global} 是 NekoJS 状态容器；语言全局对象仍是
     * {@code globalThis}（Node shim 的 {@code globalThis.global = globalThis} 别名只在无
     * 绑定的 shim 语境可见——polyglot 绑定成员会遮蔽同名 globalThis 属性，实证见票 10）。
     */
    public void installEnvironmentBindings(Context context, ScriptType scriptType, GenerationGlobals globals,
                                          NekoModulePipelineCache moduleSession) {
        if (moduleSession == null) throw new NullPointerException("moduleSession");
        try {
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

            // 受管 global/shared 状态视图（票 10）：在插件绑定之后安装，保证状态容器语义由
            // root 拥有的视图决定（不再有进程级共享 Map 绑定）；任意顶层 key 合法（动态容器）。
            bindings.putMember("global", globals.globalView());
            bindings.putMember("shared", globals.sharedView());
            bindingSchema.put("global", ScriptBindingSchema.BindingMembers.dynamicContainer());
            bindingSchema.put("shared", ScriptBindingSchema.BindingMembers.dynamicContainer());

            bindManagedGlobals(bindings, scriptType, bindingSchema, ApiGuestErrorFactory.create(context));
            addEventGroupSchema(bindingSchema, pluginRuntime.eventGroups().values(), ScriptEventRegistry.groupsFor(scriptType));

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
            ScriptBindingSchema.View schemaView = moduleSession.beginBindingSchemaCandidate(
                    moduleSession, scriptType, bindingSchema, knownGlobals, context);
            moduleSession.installBindingSchemaView(schemaView);
            installJavaClassLoadTelemetry(context, scriptType);
        } catch (Throwable failure) {
            moduleSession.discardBindingSchemaCandidate(moduleSession);
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to install NekoJS environment bindings", failure);
        }
    }

    /** Publish a successfully installed schema at the active/candidate commit point. */
    public void publishEnvironmentBindings(Context context, NekoModulePipelineCache moduleSession) {
        ScriptBindingSchema.View view = moduleSession.commitBindingSchemaCandidate(moduleSession);
        if (view == null) throw new IllegalStateException("No candidate binding schema for Context");
        moduleSession.installBindingSchemaView(view);
    }

    public void discardEnvironmentBindings(Context context, NekoModulePipelineCache moduleSession) {
        moduleSession.discardBindingSchemaCandidate(moduleSession);
    }

    /**
     * 完整创建环境（preparation + binding 两个阶段顺序执行）：创建非事务 generation
     * 的 global/shared 视图（顶层写直接提交）。供 {@link ScriptManager} 的 active 环境
     * 懒创建路径使用；事务式 reload 分两步调用（createContext + 传入候选 generation 的
     * installEnvironmentBindings）以区分失败阶段。
     */
    public Environment create(ScriptType scriptType, NekoModulePipelineCache moduleSession) {
        GenerationGlobals globals = newGeneration(scriptType, false);
        Environment environment = null;
        try {
            environment = createContext(scriptType, globals, moduleSession);
            installEnvironmentBindings(environment.context(), scriptType, globals, moduleSession);
            publishEnvironmentBindings(environment.context(), moduleSession);
            return environment;
        } catch (Throwable failure) {
            if (environment != null) {
                discardEnvironmentBindings(environment.context(), moduleSession);
                closeEnvironment(environment, failure);
            } else {
                moduleSession.closeSession();
            }
            try {
                globals.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to create NekoJS environment", failure);
        }
    }

    /**
     * 创建携带指定 generation 视图的裸环境（候选路径两步式创建的第一步；
     * BINDING 阶段由调用方执行 {@link #installEnvironmentBindings}）。
     */
    /** Create a bare environment with a generation view and module session. */
    public Environment createContext(ScriptType scriptType, GenerationGlobals globals,
                                     NekoModulePipelineCache moduleSession) {
        Environment bare = createContext(scriptType, moduleSession);
        return new Environment(bare.context(), bare.nodeRuntime(), bare.outStream(), bare.errStream(), globals);
    }

    private void bindManagedGlobals(Value bindings, ScriptType scriptType,
                                    Map<String, ScriptBindingSchema.BindingMembers> bindingSchema,
                                    ApiGuestErrorFactory guestErrorFactory) {
        EnvironmentKey key = EnvironmentKey.current(scriptType);
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

    private static void closeStream(com.tkisor.nekojs.core.log.LoggerStream stream, Throwable failure) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static void closeEnvironment(Environment environment, Throwable failure) {
        try {
            if (environment.nodeRuntime() != null) environment.nodeRuntime().close();
        } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
        }
        try {
            if (environment.context() != null) environment.context().close();
        } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
        }
        closeStream(environment.outStream(), failure);
        closeStream(environment.errStream(), failure);
    }

    /**
     * 携带 out/err {@link LoggerStream}：Graal 关闭 Context 时只 detach 用户流、不 close，
     * 由 ScriptManager 的销毁路径在 context.close() 之后补一次 close() 冲刷末行缓冲。
     * {@code globals} 是本环境的 global/shared generation 视图持有者（裸两步式创建的第一步
     * 为 null；ScriptManager 在环境销毁时对其做 guest 值失效清除）。
     */
    public record Environment(Context context, com.tkisor.nekojs.core.node.NekoNodeRuntime nodeRuntime,
                              com.tkisor.nekojs.core.log.LoggerStream outStream,
                              com.tkisor.nekojs.core.log.LoggerStream errStream,
                              GenerationGlobals globals) {

        static Environment bare(Context context, com.tkisor.nekojs.core.node.NekoNodeRuntime nodeRuntime,
                                com.tkisor.nekojs.core.log.LoggerStream outStream,
                                com.tkisor.nekojs.core.log.LoggerStream errStream) {
            return new Environment(context, nodeRuntime, outStream, errStream, null);
        }
    }
}
