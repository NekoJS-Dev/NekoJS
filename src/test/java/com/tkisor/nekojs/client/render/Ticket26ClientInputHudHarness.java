//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
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
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixture harness for the client input and HUD domain (version-tree test tree): a real root, the real
 * Graal pipeline and the real {@link ClientRenderRegistry}. Scripts register persistent renderers
 * through {@code ClientEvents.hudRender(...)}, and CLIENT scripts load and reload through the production
 * lifecycle entry points ({@code loadScripts} / {@code root.reload(CLIENT)}).
 *
 * <p>Only public behavior is observed: {@link #dispatchHud(String)} passes a {@code List} as the probe
 * for the render context, and script callbacks write {@code ctx.add(id)}. An assertion therefore gets
 * the ids of the renderers actually called this frame, not a snapshot of a private registry and not
 * callback identity. Dispatching once before and once after a reload through the same probe shows that
 * a candidate is invisible, that a commit swaps renderers, and that a failure keeps the old active
 * state.
 *
 * <p>Same shape as {@code PostEffectDeclarationHarness}: same assembly sequence, same Platform/gameDir
 * stub, same ScriptType = CLIENT, only the domain differs (input and HUD registration).
 */
public final class Ticket26ClientInputHudHarness implements AutoCloseable {

    /** Extra event groups. ClientEvents carries hudRender/hud; KeyBindEvents carries
     * register/pressed/released/tick. */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    public final Engine engine = Engine.newBuilder().build();
    public final NekoRuntimeRoot root;

    public Ticket26ClientInputHudHarness() {
        groups.put("ClientEvents", ClientEvents.GROUP);
        groups.put("KeyBindEvents", KeyBindEvents.GROUP);
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        // Same wiring as the client dist branch of NekoJSMod.registerClient: the collector of the
        // renderer registration domain must be registered so even an empty batch reaches the commit
        // point and swaps in the production tables.
        root.registerDomainCollector(new ClientRenderDomainOwner());
        root.createScriptManager(ScriptType.CLIENT).discoverScripts();
    }

    /** Platform stub and gameDir. Test-tree convention: {@code TestGameDirs.unique} isolates by PID. */
    public static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket26-input-hud");
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 26 client input/HUD tests", e);
        }
    }

    /** Writes a CLIENT script, overwriting a file with the same name. */
    public void writeClientScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** Non-transactional initial load, triggered in production from client setup
     * ({@code NekoJSClient.onClientSetup}). */
    public void loadClientScripts() throws Exception {
        var manager = root.scriptManagerOf(ScriptType.CLIENT);
        manager.discoverScripts();
        manager.loadScripts();
    }

    /** Transactional CLIENT reload, triggered in production by an F3+T resource reload. */
    public void reloadClientScripts() {
        root.reload(ScriptType.CLIENT);
    }

    /** Empties the CLIENT script directory so leftover files from one test cannot reach the next. */
    public static void clearClientScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Clears the production HUD and world renderer tables and drops the generation identity.
     * {@code clearAll()} also nulls {@code activeContext}, so the next registration installs against
     * a new generation. Independent tests therefore do not affect each other, and the existing public
     * cleanup path is used.
     */
    public static void resetRegistry() {
        ClientRenderRegistry.clearAll();
    }

    /**
     * Dispatches one frame of the given HUD layer and returns the ids of the renderers <b>actually
     * called</b>, in call order. The probe is a {@code List}: the script callback writes
     * {@code ctx.add(id)}, so what is observed here is the installed result itself.
     */
    public static List<String> dispatchHud(String layer) {
        List<String> calls = new ArrayList<>();
        ClientRenderRegistry.dispatchHud(hudLayer(layer), calls, "gui");
        return calls;
    }

    /** Whether the given HUD layer has a live renderer installed (the per-frame fast path the render hook checks). */
    public static boolean hasHud(String layer) {
        return ClientRenderRegistry.hasHud(hudLayer(layer));
    }

    private static ClientRenderRegistry.HudLayer hudLayer(String layer) {
        return switch (layer) {
            case "background" -> ClientRenderRegistry.HudLayer.BACKGROUND;
            case "normal" -> ClientRenderRegistry.HudLayer.NORMAL;
            case "foreground" -> ClientRenderRegistry.HudLayer.FOREGROUND;
            default -> throw new IllegalArgumentException("unknown hud layer " + layer);
        };
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

    /** Binds the script-facing event groups the way production does, wrapping each group in {@code EventGroupJS}. */
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

        @Override
        public ListenerBatch preparePackListeners(ScriptType type,
                List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> pending,
                java.util.Set<graal.graalvm.polyglot.Context> replaced) {
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingCommit> commits = new ArrayList<>();
            groups.values().forEach(group -> commits.addAll(group.preparePendingListeners(type, pending, replaced::contains)));
            return new ListenerBatch() {
                public void finish() { commits.forEach(com.tkisor.nekojs.api.event.EventBusJS.PendingCommit::finish); }
                public void rollback() {
                    for (int index = commits.size() - 1; index >= 0; index--) commits.get(index).rollback();
                }
            };
        }
    }

    /** Cancels the owning creator Context, never the restricted view returned to guest callbacks. */
    public static final class Recorder {
        public void cancelContext() throws ReflectiveOperationException {
            Context borrowed = Context.getCurrent();
            java.lang.reflect.Field field = ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
            field.setAccessible(true);
            if (field.get(null) instanceof Map<?, ?> owners) {
                for (Object key : owners.keySet()) {
                    if (key instanceof Context owned && owned.equals(borrowed)) {
                        owned.close(true);
                        return;
                    }
                }
            }
            throw new AssertionError("The evaluating candidate must have a creator-owned Context");
        }
    }

    /** Exposes event groups and the test-owned explicit cancellation helper. */
    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> groups;
        private final Recorder recorder = new Recorder();

        StubPluginRuntime(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("Recorder", Binding.of("Recorder", recorder));
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
//?}
