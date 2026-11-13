package com.tkisor.nekojs.network;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for owner-queue routing on the custom network channel and for the generation/stale
 * rules: after the platform enqueue / main-thread entry, the neutral dispatch core
 * ({@link NetworkMessageHandler}) routes a {@link NekoScriptPayload} into the
 * {@link NetworkEvents} bus of the matching ScriptType, and the candidate/commit/close/
 * cancellation handling of the script manager then constrains what may receive it.
 *
 * <p>The test observes real script callbacks on a real {@link ScriptManager} (Graal Context,
 * pendingListeners, the commit point, close taking priority, activeFailed isolation) and does
 * not assert on private fields. The only exception is the currentContext probe, which takes
 * the runtime context to prove that a closed Context is never touched again. The platform
 * main-thread hop (enqueueWork / server().execute()) is loader-side behavior pinned by
 * characterization and source traces, so this test's calling thread is the owner thread
 * itself, the same shape as SERVER = server thread and CLIENT = render thread in production.
 */
class NetworkGenerationRoutingTest {

    // ---- Test bindings ----

    /**
     * Counter binding injected into scripts: it counts hits per tag, so callbacks from the old and
     * the new generation can be told apart.
     */
    public static final class Recorder {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public void cancelContext() throws ReflectiveOperationException {
            var borrowed = Context.getCurrent();
            var field = com.tkisor.nekojs.script.ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
            field.setAccessible(true);
            // Only the registered creator wrapper owns the right to cancel its Context.
            for (Object key : ((Map<?, ?>) field.get(null)).keySet()) {
                if (key instanceof Context owner && owner.equals(borrowed)) {
                    owner.close(true);
                    return;
                }
            }
            throw new AssertionError("Current Context has no registered creator wrapper");
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(tag);
            return value == null ? 0 : value.get();
        }
    }

    /**
     * Java-side trigger injected into scripts: it simulates a script-channel packet arriving on the
     * platform main thread in the middle of a script run.
     */
    public static final class PacketTrigger implements AutoCloseable {
        private volatile Runnable action = () -> {};

        public void now() {
            action.run();
        }

        void bind(Runnable action) {
            this.action = action;
        }

        @Override
        public void close() {
            bind(() -> {});
        }
    }

    /**
     * Bridge that binds the real {@link NetworkEvents} group, so scripts can register server and
     * client network listeners.
     */
    static final class NetworkEventBridge implements ScriptEventBridge {
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
        private final PacketTrigger trigger;

        StubPluginRuntime(Recorder recorder, PacketTrigger trigger) {
            this.recorder = recorder;
            this.trigger = trigger;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Recorder", Binding.of("Recorder", recorder),
                    "Trigger", Binding.of("Trigger", trigger));
        }

        @Override
        public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() {
            // Same group instance the bridge holds: schema harvesting and bindEvents point at one bus.
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

    // ---- Wiring ----

    @BeforeAll
    static void initPlatform() {
        // Platform stub shaped like TestPlatformInit in the common test tree (src/test cannot
        // reference common test fixtures). Skipped when already initialized so other test classes
        // in the same JVM are not disturbed.
        try {
            Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                // gameDir is unique per test JVM (see TestGameDirs): a fixed-name tmp dir shared by parallel
                // test JVMs would let them wipe each other's fixtures during the @BeforeEach script
                // cleanup. Platform initialization is first-come-first-served, so this class may
                // attach to a directory an earlier test class in the same JVM already initialized.
                String dirId = "nekojs-nettest-" + Integer.toHexString(
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
    void cleanScriptDir() throws Exception {
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
    }

    @AfterEach
    void sweepBuses() {
        NetworkEvents.GROUP.clearListeners(ScriptType.SERVER);
        NetworkEvents.GROUP.clearListeners(ScriptType.CLIENT);
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

    private static SandboxConfig defaultSandboxConfig() {
        // Ordinary lifecycle fixtures use no production resource-control policy.
        return new SandboxConfig(false, false, false, false, true, true, false, true);
    }

    private static final class ManagerHarness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final NetworkEventBridge bridge = new NetworkEventBridge();
        final Recorder recorder = new Recorder();
        final PacketTrigger trigger = new PacketTrigger();
        final ScriptManager manager;
        final ScriptType scriptType;

        ManagerHarness(SandboxConfig config) {
            this(config, ScriptType.SERVER);
        }

        ManagerHarness(SandboxConfig config, ScriptType scriptType) {
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, trigger);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                    com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths,
                            compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                    compilers, pluginRuntime, cache);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory,
                            new com.tkisor.nekojs.core.state.GlobalStateStores());
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);
            this.trigger.bind(() -> NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("ch", new net.minecraft.nbt.CompoundTag()), null));
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

        void postServerPacket() {
            NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("ch", new net.minecraft.nbt.CompoundTag()), null);
        }

        void postClientPacket() {
            NetworkMessageHandler.postClientEvent(
                    new NekoScriptPayload("ch", new net.minecraft.nbt.CompoundTag()));
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

    // ---- fixture ----

    /**
     * Neutral dispatch after the platform main-thread entry: a SERVER packet goes to the SERVER
     * bus, reaches the listener for its channel and carries the sender.
     */
    @Test
    void serverPacketRoutesToActiveListenerByChannel() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    Recorder.hit('v1-entry');
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l:' + event.channel); });
                    NetworkEvents.server('other', function (event) { Recorder.hit('v1-other'); });
                    """);
            harness.loadAndRun();
            assertEquals(1, harness.recorder.hitsOf("v1-entry"));

            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v1-l:ch"), "packet must hit the matching-channel listener");
            assertEquals(0, harness.recorder.hitsOf("v1-other"), "other-channel listener must not fire");

            // A packet on an illegal or unknown channel keeps the "no listener, drop it" behavior: nothing
            // throws, so the caller (the platform main thread) stays up.
            assertDoesNotThrow(() -> NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("unknown_channel", new net.minecraft.nbt.CompoundTag()), null));
            assertEquals(0, harness.recorder.hitsOf("v1-other"));
        }
    }

    /**
     * A CLIENT packet only enters the CLIENT bus; SERVER listeners never see S2C packets, because
     * the buses are isolated.
     */
    @Test
    void clientPacketRoutesOnlyToClientBus() throws Exception {
        try (ManagerHarness serverHarness = new ManagerHarness(defaultSandboxConfig())) {
            serverHarness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('server-l'); });
                    """);
            serverHarness.loadAndRun();

            try (ManagerHarness clientHarness = new ManagerHarness(defaultSandboxConfig(), ScriptType.CLIENT)) {
                clientHarness.writeScript("entry.js", """
                        NetworkEvents.client('ch', function (event) { Recorder.hit('client-l:' + event.channel); });
                        """);
                clientHarness.loadAndRun();

                clientHarness.postClientPacket();
                assertEquals(1, clientHarness.recorder.hitsOf("client-l:ch"),
                        "S2C packet must hit the CLIENT listener via the CLIENT bus");
                assertEquals(0, serverHarness.recorder.hitsOf("server-l"),
                        "S2C packet must not leak into the SERVER bus");
            }
        }
    }

    /**
     * Routing across the reload commit: a packet that arrives before the commit is served by
     * the active generation, while candidate listeners stay pending (pendingListeners) and do
     * not enter production routing early. After the commit the same event is handled exactly
     * once by the new generation; the old generation receives nothing.
     */
    @Test
    void candidatePhasePacketIsServedByActiveAndNeverEntersCandidate() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v1-l"));

            // The candidate script registers its listeners first (they go into the pending collection),
            // then the packet "arrives": the packet must still be served by active v1, and the
            // candidate listeners must stay invisible.
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v2-l'); });
                    Recorder.hit('v2-entry');
                    Trigger.now();
                    """);
            harness.reload();

            assertEquals(1, harness.recorder.hitsOf("v2-entry"), "reload must run the candidate entry once");
            assertEquals(2, harness.recorder.hitsOf("v1-l"),
                    "packet arriving before commit must be served by the active generation");
            assertEquals(0, harness.recorder.hitsOf("v2-l"),
                    "candidate listener must not receive packets before commit");

            // After the commit, a new event is handled once by the new generation only; the old generation
            // is frozen.
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v2-l"), "post-commit packet handled exactly once by new generation");
            assertEquals(2, harness.recorder.hitsOf("v1-l"), "old generation listener never fires again");

            harness.postServerPacket();
            assertEquals(2, harness.recorder.hitsOf("v2-l"), "steady state: new generation keeps receiving");
            assertEquals(2, harness.recorder.hitsOf("v1-l"));

            assertTrue(harness.bridge.clearListenerCalls.contains(ScriptType.SERVER),
                    "commit must sweep old-generation network listeners via the bridge");
        }
    }

    /**
     * After two commits in a row only the newest generation receives: the generation id only moves
     * forward, so an old one never comes back.
     */
    @Test
    void consecutiveReloadsKeepOnlyLatestGenerationReceiving() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1'); });
                    """);
            harness.loadAndRun();
            long base = harness.manager.generationId();

            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v2'); });
                    """);
            harness.reload();
            long afterFirst = harness.manager.generationId();

            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v3'); });
                    """);
            harness.reload();
            long afterSecond = harness.manager.generationId();

            assertTrue(afterFirst > base && afterSecond > afterFirst, "generation must advance monotonically");

            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v3"));
            assertEquals(0, harness.recorder.hitsOf("v1"));
            assertEquals(0, harness.recorder.hitsOf("v2"));
        }
    }

    /**
     * Close behavior: close clears the network listeners of that type, an in-flight packet is
     * dropped (no listener fires and nothing throws), and the closed Context is never touched
     * again ({@code eval} throws and the manager lookup throws). Nothing revives, and the close
     * does not flood the log.
     */
    @Test
    void closeDropsInFlightPacketsAndNeverTouchesClosedContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v1-l"));

            Context closedContext = currentContext(harness.manager);
            harness.manager.close();

            // In-flight packet: dropped instead of throwing, so the runnable the platform enqueued does not
            // blow up.
            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(1, harness.recorder.hitsOf("v1-l"), "in-flight packet after close must be dropped");

            // Proof that the closed Context is never touched: eval throws and the ScriptManager lookup
            // throws, so the close did not revive it.
            assertThrows(Exception.class, () -> closedContext.eval("js", "1 + 1"));
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(closedContext));
            assertFalse(harness.manager.isActiveFailed(), "clean close is not an isolated failure");
        }
    }

    /** Explicit cancellation isolates the active generation and drops packets until an explicit reload. */
    @Test
    void isolatedActiveFailureDropsInFlightPackets() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            // Register first, then cancel explicitly; the listener remains installed until reload/close.
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    Recorder.cancelContext();
                    """);
            harness.loadAndRun();

            assertTrue(harness.manager.isActiveFailed(),
                    "explicit cancellation of the active context must enter isolated failure");

            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(0, harness.recorder.hitsOf("v1-l"),
                    "in-flight packet must be dropped for the killed context, without dispatching into it");

            // An isolated failure does not start a second active generation: the generation id stays put, and
            // only an explicit reload resumes receiving.
            long generationAtFailure = harness.manager.generationId();
            harness.postServerPacket();
            assertEquals(generationAtFailure, harness.manager.generationId());

            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v2-l'); });
                    """);
            harness.reload();
            assertFalse(harness.manager.isActiveFailed(), "explicit reload clears the isolated failure");
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v2-l"));
            assertEquals(0, harness.recorder.hitsOf("v1-l"));
        }
    }

    /**
     * Listener exceptions stay contained: the event bus catches and records a throwing listener
     * body, so the error does not reach the caller (the main-thread task the platform enqueued
     * stays up) and the other listeners on the same channel keep running.
     */
    @Test
    void listenerExceptionIsContainedAndDoesNotAbortDispatch() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { throw new Error('listener boom'); });
                    NetworkEvents.server('ch', function (event) { Recorder.hit('healthy-l'); });
                    """);
            harness.loadAndRun();

            assertDoesNotThrow(harness::postServerPacket,
                    "listener exceptions must not propagate out of the neutral post core");
            assertEquals(1, harness.recorder.hitsOf("healthy-l"),
                    "a failing listener must not abort dispatch of the remaining listeners");
        }
    }

    /** close is idempotent, and a packet that arrives after close is still safe. */
    @Test
    void repeatedCloseAndLatePacketsAreSafe() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(defaultSandboxConfig())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.manager.close();
            assertDoesNotThrow(harness.manager::close, "close is idempotent");
            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(0, harness.recorder.hitsOf("v1-l"));

            // A behavioral lower bound instead of repeating fields: one more post after close is still safe
            // and still delivers nothing.
            assertDoesNotThrow(harness::postServerPacket, "late packets stay safe after repeated close");
            assertEquals(0, harness.recorder.hitsOf("v1-l"), "no listener may fire after close");
        }
    }
}
