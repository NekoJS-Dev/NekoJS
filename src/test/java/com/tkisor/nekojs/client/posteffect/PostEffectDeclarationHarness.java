//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Script-path fixture harness for post-effect declarations (version-tree test tree): a real root, the
 * real Graal pipeline and a real {@link PostEffectDomainOwner} (the client adapter). Scripts declare
 * register/unregister through {@code ClientEvents.postEffects}, and the production domain collector
 * drives candidate collection and the commit point.
 *
 * <p>Same shape as {@code Ticket39ModificationScriptHarness}: same assembly sequence, same
 * Platform/gameDir stub, only the ScriptType differs, because CLIENT is the only ScriptType with
 * candidates in this domain. {@link #loadAndApplyInitialPlan()} simulates the non-transactional
 * initial load, which in production collects on the client main thread.
 */
final class PostEffectDeclarationHarness implements AutoCloseable {

    /** Extra event groups. By default only ClientEvents is wired. */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    final Engine engine = Engine.newBuilder().build();
    final PostEffectDomainOwner owner = new PostEffectDomainOwner();
    final NekoRuntimeRoot root;

    PostEffectDeclarationHarness() {
        groups.put("ClientEvents", ClientEvents.GROUP);
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths,
                        compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, pluginRuntime, cache);
        root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        root.registerDomainCollector(owner);
        root.createScriptManager(ScriptType.CLIENT).discoverScripts();
    }

    /** Platform stub and gameDir. Test-tree convention: {@code TestGameDirs.unique} isolates by PID. */
    static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket28-posteffects");
                gameDir.toFile().mkdirs();
                com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                    @Override public boolean isClient() { return true; }
                    @Override public boolean isDevelopment() { return true; }
                    @Override public String getMcVersion() { return "0.0.0"; }
                    @Override public Path getGameDir() { return gameDir; }
                    @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                    @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
                    @Override public java.util.Set<com.tkisor.nekojs.platform.PlatformCapability> capabilities() { return java.util.Set.of(); }
                    @Override public String getLoaderId() { return "test"; }
                    @Override public String getLoaderVersion() { return "0"; }
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Platform for ticket 28 post-effect tests", e);
        }
    }

    /** Writes a CLIENT script, overwriting a file with the same name. */
    void writeClientScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** Non-transactional initial load plus the initial generation collection point, which production
     * triggers from client setup. */
    void loadAndApplyInitialPlan() throws Exception {
        var manager = root.scriptManagerOf(ScriptType.CLIENT);
        manager.discoverScripts();
        manager.loadScripts();
        owner.applyInitialPlan();
    }

    /** Transactional CLIENT reload, triggered in production by an F3+T resource reload: collects the
     * DOMAIN_PLAN candidate and commits it. */
    void reloadClientScripts() {
        root.reload(ScriptType.CLIENT);
    }

    /** Empties the CLIENT script directory so leftover files from one test cannot reach the next. */
    static void clearClientScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Read-only snapshot of the installed definition ids as strings. It does not assert on the private
     * static map. */
    static List<String> installedIds() {
        return PostEffectManager.installedDefinitions().keySet().stream()
                .map(String::valueOf).sorted().toList();
    }

    /**
     * Clears the process-wide installed definitions so independent tests do not affect each other. It
     * goes through the installGeneration path of the production adapter and explicitly retires every
     * current id. It does not advance the owner generation, so a test starts from a clean generation.
     */
    static void resetDeclarations() {
        java.util.Set<net.minecraft.resources.Identifier> installed =
                new java.util.LinkedHashSet<>(PostEffectManager.installedDefinitions().keySet());
        PostEffectManager.installGeneration(-1L, java.util.Map.of(), installed);
    }

    @Override
    public void close() {
        try {
            groups.values().forEach(group -> group.clearListeners(ScriptType.CLIENT));
            root.closeSilently();
        } finally {
            engine.close();
        }
    }

    /** Binds the script-facing event groups the way production does, wrapping each group in
     * {@code EventGroupJS}. */
    private static final class Bridge implements ScriptEventBridge {
        private final Map<String, EventGroup> groups;

        Bridge(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override
        public void bindEvents(graal.graalvm.polyglot.Value bindings, ScriptType type) {
            groups.forEach((name, group) -> bindings.putMember(name, new EventGroupJS(group, type)));
        }

        @Override
        public void clearListeners(ScriptType type) {
            groups.values().forEach(group -> group.clearListeners(type));
        }
    }

    /** Plugin runtime stub that advertises event groups and an empty binding set. */
    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> groups;

        StubPluginRuntime(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of();
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return groups;
        }

        @Override public List<com.tkisor.nekojs.api.JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void afterRecipes(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public com.tkisor.nekojs.api.surface.ApiRuntimeView apiRuntime(com.tkisor.nekojs.api.surface.EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(com.tkisor.nekojs.api.surface.ApiSymbolId globalId) { return null; }
    }

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }
}
//?}
