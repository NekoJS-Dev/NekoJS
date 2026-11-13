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
 * Script environment factory: owns Context, Node, bindings, event, and telemetry initialization.
 *
 * <p>This logic was moved down from {@link ScriptManager}'s {@code getOrCreateContext} and
 * {@code installJavaClassLoadTelemetry}. {@code ScriptManager} keeps the top-level discover/load/reload/close lifecycle
 * and no longer handles Context initialization details.
 *
 * <p>{@code create(ScriptType, NekoModulePipelineCache)} returns an {@link Environment} that holds a {@link Context},
 * a {@link com.tkisor.nekojs.core.node.NekoNodeRuntime}, and the close/unbind semantics. The
 * {@link ScriptContextRegistry} is bound here after the Context is created.
 */
public final class ScriptEnvironmentFactory {
    private final ScriptEventBridge eventBridge;
    private final IPluginRuntime pluginRuntime;
    private final NekoSandboxFactory sandboxFactory;
    /** Global and shared state owned by the root: views are installed with an environment, while
     *  the stores live as long as the root. */
    private final GlobalStateStores globalStores;

    public ScriptEnvironmentFactory(ScriptEventBridge eventBridge, IPluginRuntime pluginRuntime,
                                    NekoSandboxFactory sandboxFactory, GlobalStateStores globalStores) {
        this.eventBridge = eventBridge;
        this.pluginRuntime = pluginRuntime;
        this.sandboxFactory = sandboxFactory;
        this.globalStores = globalStores;
    }

    /** The root state this factory is wired to; ScriptManager creates a generation view for each environment. */
    public GlobalStateStores globalStores() {
        return globalStores;
    }

    public com.tkisor.nekojs.core.ui.BuiltinUiRuntime createBuiltinUiRuntime() {
        return sandboxFactory.createBuiltinUiRuntime();
    }

    /**
     * Creates the holder for one generation's {@code global}/{@code shared} views: a candidate write
     * set when transactional, direct commits otherwise.
     */
    public GenerationGlobals newGeneration(ScriptType scriptType, boolean transactional) {
        return globalStores.newGeneration(scriptType, transactional);
    }

    /**
     * Creates the bare environment for a candidate or active generation: a sandbox Context plus the
     * node runtime, <b>without</b> any event groups, plugin bindings or schema installation.
     *
     * <p>Generation isolation: preparation (this method) and binding installation
     * ({@link #installEnvironmentBindings}) are two separate stages, so a failed reload can report
     * whether it failed in the PREPARATION or in the BINDING stage.
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
     * Installs event group bindings, plugin bindings, managed globals, the {@code global}/{@code shared}
     * state views, the binding schema and class-load telemetry into the given Context (BINDING stage).
     *
     * <p>Every write targets {@code context.getBindings("js")}, so a candidate Context and the active
     * Context each own their member table; a failed installation only affects the candidate environment
     * and never touches the active one.
     *
     * <p>The value bound to {@code global}/{@code shared} is <b>that generation's view</b>
     * ({@link GenerationGlobals}), not a shared Map instance: top-level writes from a candidate
     * generation go into the candidate write set, and the active generation commits directly. This keeps
     * the binding definition (name and schema) apart from the per-generation view. {@code global} is the
     * NekoJS state container; the language global object is still {@code globalThis} (the Node shim alias
     * {@code globalThis.global = globalThis} is visible only in shim contexts without bindings, because
     * polyglot binding members shadow a globalThis property of the same name).
     */
    public void installEnvironmentBindings(Context context, ScriptType scriptType, GenerationGlobals globals,
                                          NekoModulePipelineCache moduleSession) {
        if (moduleSession == null) throw new NullPointerException("moduleSession");
        try {
            var bindings = context.getBindings("js");
            eventBridge.bindEvents(bindings, scriptType);

            com.tkisor.nekojs.core.api.ApiModuleInstaller.install(context, pluginRuntime, scriptType);
            var environmentBindings = pluginRuntime.bindings(scriptType);
            Map<String, ScriptBindingSchema.BindingMembers> bindingSchema = new HashMap<>();
            if (bindings.hasMember("Pack")) {
                if (environmentBindings.containsKey("Pack")) throw new IllegalStateException("[NEKO-1017] Plugin binding conflicts with the versioned package binding: Pack");
                bindingSchema.put("Pack", new ScriptBindingSchema.BindingMembers(Set.of("provide", "require", "selector", "activation")));
            }
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

            // Managed global/shared state views: installed after the plugin bindings, so the state container
            // semantics come from the root-owned views (there is no process-wide shared Map binding).
            // Any top-level key is valid; the container is dynamic.
            bindings.putMember("global", globals.globalView());
            bindings.putMember("shared", globals.sharedView());
            bindingSchema.put("global", ScriptBindingSchema.BindingMembers.dynamicContainer());
            bindingSchema.put("shared", ScriptBindingSchema.BindingMembers.dynamicContainer());

            bindManagedGlobals(bindings, scriptType, bindingSchema, ApiGuestErrorFactory.create(context));
            addEventGroupSchema(bindingSchema, pluginRuntime.eventGroups().values(), ScriptEventRegistry.groupsFor(scriptType));

            // Known globals for the undefined-identifier check: whatever the runtime Context really exposes —
            // every globalThis property name (JS built-ins plus engine globals such as console) unioned
            // with the polyglot binding keys (bindings the platform installed), plus the keywords the
            // parser treats as identifiers (this/arguments/super). Either source alone misses entries:
            // console is not a binding key, and bindings the guest side injects are not all on
            // globalThis.
            Set<String> knownGlobals;
            // Batch only guest inspection; plugin callbacks retain their caller's current Context.
            context.enter();
            try {
                knownGlobals = new LinkedHashSet<>(context.getBindings("js").getMemberKeys());
                Value globalNames = context.eval("js", "Object.getOwnPropertyNames(globalThis)");
                if (globalNames.hasArrayElements()) {
                    for (long i = 0; i < globalNames.getArraySize(); i++) {
                        knownGlobals.add(globalNames.getArrayElement(i).asString());
                    }
                }
            } finally {
                context.leave();
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
     * Creates a complete environment, running the preparation and binding stages in order: it creates
     * the {@code global}/{@code shared} views of a non-transactional generation, so top-level writes
     * commit directly. Used by the {@link ScriptManager} lazy creation path for the active environment.
     * A transactional reload calls the two steps separately ({@code createContext}, then
     * installEnvironmentBindings with the candidate generation) to tell the failure stages apart.
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
     * Creates a bare environment that carries the given generation view. This is the first of the two
     * steps on the candidate path; the caller runs {@link #installEnvironmentBindings} for the BINDING
     * stage.
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
     * Event groups are global bindings too ({@code ServerEvents}/{@code BlockEvents}/… are bound by
     * eventBridge.bindEvents), so they must also go into {@link ScriptBindingSchema}:
     * {@code EventCallbackSourceValidator} uses {@code schema.containsKey("ServerEvents")} to decide
     * that a call is an event-group call before it validates the callback body. Without this step the
     * event callback preflight (such as the {@code event.recipes} spelling check) silently stops working
     * in production; unit tests registered the schema by hand, which hid the gap. Valid members of a
     * group are its event names (bus names).
     *
     * <p>Members of the ScriptEvents custom groups ({@code ScriptEventGroupJS}) are the registered
     * definition names. Existing entries (environment bindings, managed globals) are not overwritten;
     * custom groups use {@code putIfAbsent}.
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
     * Holds the out/err {@link LoggerStream}s: when Graal closes a Context it only detaches user streams
     * and never closes them, so the ScriptManager destruction path calls close() once more after
     * context.close() to flush the last buffered line. {@code globals} is this environment's
     * {@code global}/{@code shared} generation view holder; it is null in the first step of the bare
     * two-step creation, and ScriptManager clears the guest values in it when the environment is
     * destroyed.
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
