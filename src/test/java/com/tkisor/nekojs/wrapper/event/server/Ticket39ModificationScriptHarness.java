package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.ItemEvents;
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
 * End-to-end harness for the script path (version-tree test tree; all five nodes share this
 * code and the tests run only where a vanilla registry is available): a real root, the real
 * Graal pipeline and the <b>real</b> {@link ModificationDomainOwner} (platform Adapter).
 * Scripts collect declarations through the {@code ItemEvents.modification} event surface, and
 * the commit point applies them to real Item/Block instances.
 *
 * <p>Division of labour with {@code Ticket39DomainCollectionTest} in the common layer, which
 * uses a synthetic Adapter: this harness covers the whole path from a script event to an
 * observable result in the platform Adapter, while the common test covers the collection
 * attachment points the root owns and the collection mechanics. Assertions go through public
 * seams only (scripts, reload success or failure, Item component read-back,
 * {@code owner.lastDiagnostics()}).
 *
 * <p>The platform stub and gameDir follow the test tree convention ({@code TestPlatformInit}
 * and {@code TestGameDirs.unique}); each test root is independent and does not leak into other
 * cases.
 */
final class Ticket39ModificationScriptHarness implements AutoCloseable {

    /** Extra event groups. The block half passes {@code BlockEvents.GROUP} in the 26.x tests. */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    final Engine engine = Engine.newBuilder().build();
    final ModificationDomainOwner owner = new ModificationDomainOwner();
    final NekoRuntimeRoot root;

    Ticket39ModificationScriptHarness(Map<String, EventGroup> extraGroups) {
        groups.put("ItemEvents", ItemEvents.GROUP);
        groups.putAll(extraGroups);
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
        root.createScriptManager(ScriptType.SERVER).discoverScripts();
    }

    /** Platform stub and gameDir. The test tree uses {@code TestGameDirs.unique}, isolated by PID. */
    static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket39-e2e");
                gameDir.toFile().mkdirs();
                com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                    @Override public boolean isClient() { return false; }
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 39 script E2E tests", e);
        }
    }

    /** Writes a server script, overwriting any file with the same name. */
    void writeServerScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** Collection point of the initial generation; production triggers it on server about-to-start. */
    void applyInitialPlan() {
        owner.applyInitialPlan(null);
    }

    /** Clears the server script directory so one case leaves nothing behind for the next. */
    static void clearServerScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Override
    public void close() {
        try {
            groups.values().forEach(group -> group.clearListeners(ScriptType.SERVER));
            root.closeSilently();
        } finally {
            engine.close();
        }
    }

    /** Script-side event group binding, shaped like production where {@code EventGroupJS} wraps each group. */
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

    /** Plugin runtime stub: it exposes the event groups and nothing to bind. */
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
