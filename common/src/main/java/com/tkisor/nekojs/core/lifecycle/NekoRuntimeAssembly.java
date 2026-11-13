package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import com.tkisor.nekojs.core.error.ErrorTrackerReporter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoRuntimeTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.List;

/**
 * Runtime assembly sequence shared by both loaders (NeoForge {@code NekoJSMod} and Fabric
 * {@code NekoJSFabricMod}). This is a pure construction function, <b>not</b> a new runtime owner:
 * it caches no static state, provides no get()/current(), and the caller (the loader entry) keeps
 * the result privately.
 *
 * <p>Assembly order:
 * <ol>
 *   <li>{@link NekoPluginRuntime#bootstrapOwned} (the loader finishes plugin discovery before
 *       this)</li>
 *   <li>{@link NekoRuntimeAccess#get()}.fireInit() (the init hook that runs before
 *       initStartup)</li>
 *   <li>loader-specific event wiring ({@link PluginWiring}: registrar bind / bridge set, in the
 *       order each platform keeps)</li>
 *   <li>engine context: compilers -> SandboxConfig -> ClassFilter.INSTANCE -> ErrorTracker ->
 *       ScriptErrorReporter -> NekoCoreContext (NekoSharedEngine) -> NekoSandboxFactory</li>
 *   <li>explicit language pipeline: one NekoModulePipeline and one NekoModulePipelineCache
 *       instance, shared by the sandbox factory and the root (no static binding, no legacy
 *       instance)</li>
 *   <li>{@link NekoRuntimeRoot} construction (the single lifecycle owner)</li>
 *   <li>createScriptManager + discoverScripts for each {@link ScriptType#autoLoadTypes()}</li>
 *   <li>STARTUP loadScripts</li>
 *   <li>{@link NekoRuntimeAccess#get()}.fireInitStartup()</li>
 * </ol>
 * The per-platform finishing steps (such as {@code GoalEvents.postRegister()}) stay in the loader
 * entry.
 *
 * <p>Process-level exceptions (NekoSharedEngine / NekoRuntimeAccess / NekoPluginRuntime publish)
 * are touched exactly once at fixed points in this sequence, so sharing the assembly does not
 * create a second set of state.
 */
public final class NekoRuntimeAssembly {

    private NekoRuntimeAssembly() {}

    /** Assembly result: {@code root} is the only lifecycle entry point and is held privately by the loader entry. */
    /**
     * Only root: the caller (the loader entry) consumes nothing else, which keeps the assembly from
     * growing a second access path.
     */
    public record Assembled(NekoRuntimeRoot root) {}

    /**
     * Loader-specific event wiring. Runs once, after fireInit and before the engine is assembled.
     */
    @FunctionalInterface
    public interface PluginWiring {
        void wire(NekoPluginRuntime pluginRuntime);
    }

    /**
     * Runs the full assembly sequence. {@code ownedPlugins} comes from loader-side discovery
     * ({@code NekoJSBasePluginManager.getOwnedPlugins()}); assembly itself does not touch loader
     * discovery.
     */
    public static Assembled assemble(
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            List<OwnedPlugin> ownedPlugins,
            PluginWiring pluginWiring) {
        return assemble(eventBridge, scriptProperties, ownedPlugins, pluginWiring, NekoRuntimeTrustContext.local());
    }

    /**
     * Assembly for an explicitly authorized remote pack. The default overload above stays the
     * local-trusted production path. Only the preparation cache and its resolution collaborators
     * carry the context; execution APIs never do.
     */
    public static Assembled assemble(
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            List<OwnedPlugin> ownedPlugins,
            PluginWiring pluginWiring,
            NekoTrustContext trustContext) {
        if (trustContext == null) {
            throw new NullPointerException("trustContext");
        }
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrapOwned(ownedPlugins, scriptProperties);
        // This line must appear exactly once per process (repeated bootstrapOwned calls are
        // rejected by publish). The smoke test greps for it before and after a reload to confirm
        // the count stays the same; the Fabric side has its own entrypoint/bootstrap-done marker.
        NekoJSLoggers.get("NekoJS").info("NekoJS plugin runtime bootstrapped once (assembly)");
        NekoRuntimeAccess.get().fireInit();
        pluginWiring.wire(pluginRuntime);

        var compilers = ScriptCompilerRegistry.current();
        SandboxConfig sandboxConfig = ClassFilter.loadEngineConfig();
        // Reuse the global singleton: NekoSecurityWarningHandler and others read
        // ClassFilter.INSTANCE, so a second instance would split the state.
        ClassFilter classFilter = ClassFilter.INSTANCE;
        SourceMapRegistry sourceMaps = new SourceMapRegistry(NekoJSPaths.get().root());
        NekoEsmVirtualModuleRegistry virtualModules = new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root());
        var errorTracker = new DefaultErrorTracker(NekoJSPaths.get(), sandboxConfig, sourceMaps, virtualModules);
        ScriptErrorReporter.set(new ErrorTrackerReporter(errorTracker));
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(),
                sandboxConfig,
                classFilter,
                errorTracker
        );
        // Language pipeline: a single pipeline and a single prepared cache instance, shared with the
        // execution environment and the root. The root owns the module cache and session lifetime
        // and clears everything on close; there is no static state.
        NekoModulePipelineCache modulePreparationCache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig),
                sourceMaps, virtualModules, trustContext);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, NekoJSPaths.get(), compilers, pluginRuntime, modulePreparationCache);
        NekoRuntimeRoot root = new NekoRuntimeRoot(
                core,
                pluginRuntime,
                eventBridge,
                scriptProperties,
                sandboxFactory,
                modulePreparationCache,
                trustContext
        );

        com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().discoverLoadedModPacks();
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            root.createScriptManager(type).discoverScripts();
        }

        root.load(ScriptType.STARTUP);
        NekoRuntimeAccess.get().fireInitStartup();
        root.initializeAuthoring();
        return new Assembled(root);
    }
}
