package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.bindings.static_access.ClientDataJS;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.NekoScriptPayload;
import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import com.tkisor.nekojs.wrapper.pdata.PersistentDataJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for generation boundaries and reload-retained state: the PData mirror and the ClientData
 * store are <b>process-level</b> client state. The receive side (acceptClientSync /
 * ClientDataStore.accept) only writes Java state and never enters any Graal Context, so a
 * SERVER/CLIENT reload, a failed candidate and root close all leave them alone. Only platform
 * domain hooks (disconnect, leaving the old world) clear them. Scripts read the same state through
 * their bindings, so reads give the same results before and after a reload.
 *
 * <p>Entity pdata (the server-side persistent container) likewise survives reloads: container data
 * a script writes is saved with the entity, and a reload only rebuilds the script environment. This
 * test pins the JVM-reachable part of that by comparing player/entity persistent data and script
 * reads before and after a SERVER reload. The joinLevel window and saving to disk with real
 * entities and saves are covered by node-local and characterization tests.
 */
class DataSyncGenerationBoundaryTest {

    // ---- Test binding objects ----

    public static final class Recorder {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final CountDownLatch cancellationReady = new CountDownLatch(1);
        private volatile Context cancellationContext;

        public void readyForCancellation() {
            cancellationContext = Context.getCurrent();
            cancellationReady.countDown();
        }

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(String.valueOf(tag));
            return value == null ? 0 : value.get();
        }

        public void record(String tag, Object value) {
            hits.put(String.valueOf(tag), new AtomicInteger(value instanceof Number n ? n.intValue() : -1));
        }

        public int valueOf(String tag) {
            AtomicInteger value = hits.get(String.valueOf(tag));
            return value == null ? 0 : value.get();
        }
    }

    /** Entity pdata facade: exposes the in-memory container (by id) for scripts to read and write.
     * The production equivalent is EntityExtension#neko$pdata. */
    public static final class PDataFacade {
        private final int entityId;

        PDataFacade(int entityId) {
            this.entityId = entityId;
        }

        public CompoundTag tag() {
            return EntityPDataStore.get().get(entityId, EntityExtension.NEKO_PDATA_KEY);
        }

        /** Version-independent int read (26.x getIntOr, 1.21.1 getInt). */
        public int read(String key) {
//? if >=26 {
            return tag().getIntOr(key, -1);
//?} else {
            /*return tag().getInt(key);
*///?}
        }

        public void put(String key, int value) {
            CompoundTag tag = tag();
            tag.putInt(key, value);
            EntityPDataStore.get().set(entityId, EntityExtension.NEKO_PDATA_KEY, tag);
        }
    }

    static final class DataSyncEventBridge implements ScriptEventBridge {
        final List<ScriptType> clearListenerCalls = new CopyOnWriteArrayList<>();

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindings.putMember("NetworkEvents", new EventGroupJS(NetworkEvents.GROUP, type));
        }

        @Override
        public void clearListeners(ScriptType type) {
            clearListenerCalls.add(type);
            NetworkEvents.GROUP.clearListeners(type);
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;
        private final PDataFacade pdata;

        StubPluginRuntime(Recorder recorder, PDataFacade pdata) {
            this.recorder = recorder;
            this.pdata = pdata;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Recorder", Binding.of("Recorder", recorder),
                    "Pdata", Binding.of("Pdata", pdata),
                    "clientData", Binding.of("clientData", new ClientDataJS()));
        }

        @Override
        public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() {
            return Map.of("NetworkEvents", NetworkEvents.GROUP);
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

    // ---- Assembly (same shape as the NetworkGenerationRoutingTest harness) ----

    @BeforeAll
    static void initPlatform() {
        try {
            Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                String dirId = "nekojs-datasynctest-" + Integer.toHexString(
                        Path.of("").toAbsolutePath().toRealPath().hashCode());
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique(dirId);
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
            throw new RuntimeException("Failed to initialize Platform for tests", e);
        }
    }

    @BeforeEach
    void cleanState() throws Exception {
        NetworkEvents.GROUP.clearListeners(ScriptType.SERVER);
        NetworkEvents.GROUP.clearListeners(ScriptType.CLIENT);
        for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.CLIENT)) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        ClientDataStore.SHARED.clear();
        PDataSyncService.clearClientMirrors();
        EntityPDataStore.install(memoryAccess());
    }

    @AfterEach
    void sweepState() {
        NetworkEvents.GROUP.clearListeners(ScriptType.SERVER);
        NetworkEvents.GROUP.clearListeners(ScriptType.CLIENT);
        ClientDataStore.SHARED.clear();
        PDataSyncService.clearClientMirrors();
        EntityPDataStore.install(memoryAccess());
    }

    /** In-memory container keyed by id, the same shape as EntityPDataStoreTest. The joinLevel window
     * semantics are outside this test; see the trace tests and node fixtures. */
    private static EntityPDataStore.Access memoryAccess() {
        Map<Integer, CompoundTag> containers = new HashMap<>();
        return new EntityPDataStore.Access() {
            private CompoundTag container(int entityId) {
                return containers.computeIfAbsent(entityId, ignored -> new CompoundTag());
            }

            @Override
            public CompoundTag get(int entityId, String key) {
//? if >=26 {
                return container(entityId).getCompound(key).orElseGet(CompoundTag::new).copy();
//?} else {
                /*return container(entityId).getCompound(key).copy();
*///?}
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                CompoundTag container = container(entityId);
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
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

    private static final class ManagerHarness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final DataSyncEventBridge bridge = new DataSyncEventBridge();
        final Recorder recorder = new Recorder();
        final PDataFacade pdata = new PDataFacade(42);
        final ScriptManager manager;
        final ScriptType scriptType;

        ManagerHarness(ScriptType scriptType) {
            SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, pdata);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                    com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths,
                            compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                    compilers, pluginRuntime, cache);
            ScriptEnvironmentFactory environmentFactory =
                    // Since ScriptEnvironmentFactory takes the root-owned state domains (4-arg constructor).
                    // This test does not cover the global/shared side, so pass a separate empty store.
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory,
                            new com.tkisor.nekojs.core.state.GlobalStateStores());
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);
        }

        void writeScript(String fileName, String source) throws Exception {
            Files.writeString(ScriptTypeEnv.scriptsDir(scriptType).resolve(fileName), source);
        }

        void loadAndRun() {
            manager.discoverScripts();
            manager.loadScripts();
        }

        void reload() {
            manager.reloadScripts();
        }

        void reloadWithCancelledCandidate() {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread canceller = new Thread(() -> {
                try {
                    assertTrue(recorder.cancellationReady.await(5, TimeUnit.SECONDS),
                            "Candidate must reach its cancellation checkpoint");
                    Field candidate = ScriptManager.class.getDeclaredField("candidateContext");
                    candidate.setAccessible(true);
                    Context owned = (Context) candidate.get(manager);
                    assertTrue(owned != null, "Candidate owner must retain its creator Context");
                    assertEquals(recorder.cancellationContext, owned, "Checkpoint must identify the owned candidate");
                    owned.close(true);
                } catch (Throwable cancellationError) {
                    failure.set(cancellationError);
                }
            }, "data-sync-candidate-canceller");
            canceller.setDaemon(true);
            canceller.start();
            try {
                reload();
            } finally {
                try {
                    canceller.join(5_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while joining the candidate canceller", interrupted);
                } finally {
                    if (canceller.isAlive()) canceller.interrupt();
                }
                assertFalse(canceller.isAlive(), "Test-owned cancellation must finish before fixture cleanup");
                assertNull(failure.get(), "External cancellation must not fail");
            }
        }

        void postServerProbe() {
            NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("probe", new CompoundTag()), null);
        }

        @Override
        public void close() {
            manager.close();
            engine.close();
        }
    }

    private static Context currentContext(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object environment = field.get(manager);
        Method contextAccessor = environment.getClass().getDeclaredMethod("context");
        contextAccessor.setAccessible(true);
        return (Context) contextAccessor.invoke(environment);
    }

    private static void pushClientData(String key, String json) {
        ClientDataStore.SHARED.accept(key, com.google.gson.JsonParser.parseString(json));
    }

    // ---- fixture ----

    /**
     * Reload retention: the ClientData store and the pdata mirror (process-level client state) stay
     * in place across a SERVER reload, and script reads give the same result. A reload only rebuilds
     * the script environment and never touches the data sync domain.
     */
    @Test
    void serverReloadKeepsClientStoresAndScriptReadsStable() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            pushClientData("tick:counter", "303");
            harness.writeScript("entry.js", """
                    Recorder.record('read', clientData.get('tick:counter'));
                    NetworkEvents.server('probe', function () { Recorder.hit('l1'); });
                    """);
            harness.loadAndRun();
            assertEquals(303, harness.recorder.valueOf("read"), "v1 script must read the synced value");

            harness.writeScript("entry.js", """
                    Recorder.record('read2', clientData.get('tick:counter'));
                    NetworkEvents.server('probe', function () { Recorder.hit('l2'); });
                    """);
            harness.reload();
            assertEquals(303, harness.recorder.valueOf("read2"),
                    "v2 generation must read the same value after reload (store survives reload)");

            harness.postServerProbe();
            assertEquals(1, harness.recorder.hitsOf("l2"), "new generation serves events");
            assertEquals(0, harness.recorder.hitsOf("l1"), "old generation is swept");
        }
    }

    /** A CLIENT reload (the same shape as an F3+T resource reload) does not clear the stores either. */
    @Test
    void clientReloadKeepsClientStores() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.CLIENT)) {
            pushClientData("hud:mana", "77");
            harness.writeScript("entry.js", """
                    Recorder.record('v1', clientData.get('hud:mana'));
                    """);
            harness.loadAndRun();
            assertEquals(77, harness.recorder.valueOf("v1"));

            harness.writeScript("entry.js", """
                    Recorder.record('v2', clientData.get('hud:mana'));
                    """);
            harness.reload();
            assertEquals(77, harness.recorder.valueOf("v2"), "client data must survive CLIENT reload");
        }
    }

    /** External candidate cancellation must preserve stores, active generation identity, and live listeners. */
    @Test
    void failedCandidateKeepsStoresAndActiveReads() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            pushClientData("tick:counter", "55");
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () {
                        Recorder.record('active-read', clientData.get('tick:counter'));
                        Recorder.hit('active-l');
                    });
                    """);
            harness.loadAndRun();

            Context active = currentContext(harness.manager);
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () { Recorder.hit('candidate-l'); });
                    Recorder.readyForCancellation();
                    const end = Date.now() + 2_000;
                    for (let spin = 0; spin < 1_000_000_000 && Date.now() < end; spin++) {}
                    """);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate,
                    "Externally cancelled candidate must fail the reload");
            assertEquals(active, currentContext(harness.manager),
                    "Failed candidate must not replace the active generation");

            harness.postServerProbe();
            assertEquals(55, harness.recorder.valueOf("active-read"),
                    "active generation must still read the same value after a failed reload");
            assertEquals(1, harness.recorder.hitsOf("active-l"), "active listener survives the failed candidate");
            assertEquals(0, harness.recorder.hitsOf("candidate-l"), "candidate listener never activated");
        }
    }

    /**
     * Root close: close only shuts the script environment. The receive side (acceptClientSync /
     * ClientDataStore.accept) depends on no Context, so a packet that arrives after close is still
     * written safely, can be read back and does not throw; the closed Context is never touched. Only
     * platform domain hooks (disconnect, leaving the old world) clear the mirror/store for real, not
     * root close.
     */
    @Test
    void closeKeepsReceiveFaceSafeAndNeverTouchesClosedContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () { Recorder.hit('l1'); });
                    """);
            harness.loadAndRun();

            Context closedContext = currentContext(harness.manager);
            harness.manager.close();

            // An in-flight packet reaches the receive side after close: safe, no exception, and the
            // write still takes effect because the state is process-level
            CompoundTag mirrorTag = new CompoundTag();
            mirrorTag.putInt("mana", 9);
            assertDoesNotThrow(() -> PDataSyncService.acceptClientSync(
                    new com.tkisor.nekojs.network.PDataSyncPacket(7, 1, mirrorTag)));
            assertDoesNotThrow(() -> pushClientData("late:key", "1"));
            assertTrue(PDataSyncService.hasPendingClientData(7), "mirror write after close is safe");
            assertEquals(1L, ClientDataStore.SHARED.get("late:key"), "client data write after close is safe");

            // The script callback side, which enters the Context, is gone after close: the packet is
            // dropped and the closed Context does not come back
            assertDoesNotThrow(harness::postServerProbe);
            assertEquals(0, harness.recorder.hitsOf("l1"), "no callback may fire after close");
            assertThrows(Exception.class, () -> closedContext.eval("js", "1 + 1"),
                    "closed context must never be touched again");
        }
    }

    /**
     * Data side of "a reload does not roll back world side effects": entity pdata a script writes is
     * a persistent container saved with the entity, so it survives a reload and script reads match
     * across it. A reload only rebuilds the script environment; it does not roll back container data.
     */
    @Test
    void serverReloadDoesNotRollBackEntityPData() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            harness.writeScript("entry.js", """
                    Pdata.put('ticket18', 303);
                    Recorder.record('v1', Pdata.read('ticket18'));
                    """);
            harness.loadAndRun();
            assertEquals(303, harness.recorder.valueOf("v1"));

            harness.writeScript("entry.js", """
                    Recorder.record('v2', Pdata.read('ticket18'));
                    """);
            harness.reload();
            assertEquals(303, harness.recorder.valueOf("v2"),
                    "entity pdata written by v1 must survive the reload and read back identically");
        }
    }

    /**
     * Checks that the receive side and the script environment are independent: a mirror accept
     * writes is visible to scripts through their bindings, and the revision deduplication semantics
     * do not change with a reload, so the domain semantics are not coupled to a generation.
     */
    @Test
    void receiveFaceFeedsMirrorsIndependentOfGenerations() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.CLIENT)) {
            harness.writeScript("entry.js", """
                    Recorder.record('before', clientData.get('hud:mana'));
                    """);
            harness.loadAndRun();
            assertEquals(-1, harness.recorder.valueOf("before"),
                    "nothing synced yet: clientData.get reads back as script null (recorded as -1)");

            // The server pushes a packet that arrives here (PData and ClientData in the same batch)
            CompoundTag mirrorTag = new CompoundTag();
            mirrorTag.putInt("mana", 12);
            PDataSyncService.acceptClientSync(new com.tkisor.nekojs.network.PDataSyncPacket(7, 2, mirrorTag));
            pushClientData("hud:mana", "12");

            harness.writeScript("entry.js", """
                    Recorder.record('after', clientData.get('hud:mana'));
                    """);
            harness.reload();
            assertEquals(12, harness.recorder.valueOf("after"), "synced data must be visible to the new generation");

            // A stale revision is still rejected after the reload; the domain semantics do not depend
            // on the generation
            CompoundTag staleTag = new CompoundTag();
            staleTag.putInt("mana", 99);
            PDataSyncService.acceptClientSync(new com.tkisor.nekojs.network.PDataSyncPacket(7, 1, staleTag));
            assertEquals(mirrorTag, PDataSyncService.clientMirrorById(7),
                    "stale revision must stay rejected regardless of generation changes");
        }
    }

    /** The server-side write face of PersistentDataJS (the dirty/syncer wiring) still points at the
     * same container after a reload. */
    @Test
    void persistentDataJSKeepsServingTheSameContainerAcrossReloads() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            AtomicInteger dirty = new AtomicInteger();
            PersistentDataJS pdata = new PersistentDataJS(
                    () -> EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY),
                    tag -> EntityPDataStore.get().set(42, EntityExtension.NEKO_PDATA_KEY, tag),
                    dirty::incrementAndGet,
                    () -> {});
            pdata.putInt("k", 1);
            assertEquals(1, dirty.get());

            harness.reload(); // A transaction reload with no script change also runs the full candidate/commit path

            pdata.putInt("k", 2);
            assertEquals(2, dirty.get(), "the write face keeps working after reload");
//? if >=26 {
            assertEquals(2, EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY).getIntOr("k", -1),
                    "and keeps hitting the same container");
//?} else {
            /*assertEquals(2, EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY).getInt("k"),
                    "and keeps hitting the same container");
*///?}
        }
    }
}
