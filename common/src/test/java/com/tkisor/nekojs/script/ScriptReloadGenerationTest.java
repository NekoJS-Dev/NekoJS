package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the candidate generation behavior of {@code /nekojs reload} and pins it so later
 * changes cannot break it.
 *
 * <p>These behaviors must not change: a successful reload re-runs the scripts, a failed reload
 * keeps the old {@code Context}, nothing executes twice after commit, and the production timer
 * dispatch has exactly one owner.
 */
class ScriptReloadGenerationTest {

    /** Listener event payload. */
    public static final class TestEvent {}

    /** Counter binding injected into scripts: counts per tag, so callbacks from the old and the new
     * generation can be told apart. */
    public static final class Counter {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(tag);
            return value == null ? 0 : value.get();
        }
    }

    /** Captures the executing candidate for cancellation from a test-owned thread. */
    public static final class CancellationGate {
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile Context context;

        public void ready() {
            context = Context.getCurrent();
            ready.countDown();
        }
    }

    // Both bounds let guest execution finish even if the external canceller fails.
    private static final String CANCELLED_CANDIDATE = """
            Cancellation.ready();
            const cancellationDeadline = Date.now() + 2_000;
            for (let i = 0; i < 1_000_000_000 && Date.now() < cancellationDeadline; i++) {}
            """;

    /** Java-side event trigger injected into scripts: posts a production event synchronously while a
     * script is running. */
    public static final class Trigger implements AutoCloseable {
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

    /** Bridge over a real {@link EventGroup} (the TestEvents.ping and TestEvents.lang buses) that
     * scripts can register listeners on. */
    static final class TestEventBridge implements ScriptEventBridge {
        final EventGroup group = EventGroup.of("TestEvents");
        final EventBusJS<TestEvent, Void> pingBus;
        /** Dispatch bus with a String key, used by the illegal dispatch key failure injection case. */
        final EventBusJS<TestEvent, String> langBus;
        final List<ScriptType> bindEventsCalls = new CopyOnWriteArrayList<>();
        final List<ScriptType> clearListenersCalls = new CopyOnWriteArrayList<>();
        /** Snapshot of the active runtime at each clearListeners call, used to assert the commit order. */
        final List<Object> runtimeAtSweep = new CopyOnWriteArrayList<>();
        /** Filled in by {@link ManagerHarness} at the end of its constructor so clearListeners can read
         * the owner state. */
        volatile ScriptManager manager;

        TestEventBridge() {
            this.pingBus = group.server("ping", TestEvent.class);
            this.langBus = group.server("lang", TestEvent.class, DispatchKey.string());
        }

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindEventsCalls.add(type);
            bindings.putMember("TestEvents", new EventGroupJS(group, type));
        }

        @Override
        public void clearListeners(ScriptType type) {
            clearListenersCalls.add(type);
            ScriptManager current = manager;
            if (current != null) {
                runtimeAtSweep.add(runtimeOf(current));
            }
            group.clearListeners(type);
        }

        /** Fires one production event dispatch; the test checks the Counter afterwards. */
        void postTestEvent() {
            pingBus.post(new TestEvent());
        }

        boolean busHasListeners() {
            return pingBus.hasListeners();
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Counter counter;
        private final EventGroup sharedGroup;
        private final Trigger trigger;
        private final CancellationGate cancellation;
        final List<ScriptType> closedBindings = new CopyOnWriteArrayList<>();

        StubPluginRuntime(Counter counter, EventGroup sharedGroup, Trigger trigger, CancellationGate cancellation) {
            this.counter = counter;
            this.sharedGroup = sharedGroup;
            this.trigger = trigger;
            this.cancellation = cancellation;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Counter", binding("Counter", counter),
                    "Trigger", binding("Trigger", trigger),
                    "Cancellation", binding("Cancellation", cancellation));
        }

        private Binding binding(String name, Object value) {
            return new Binding() {
                @Override public String name() { return name; }
                @Override public Object value() { return value; }
                @Override public void close(ScriptType type) { closedBindings.add(type); }
            };
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            // Holds the same group instance as the bridge, so schema collection and bindEvents use one bus
            return Map.of("TestEvents", sharedGroup);
        }

        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }

    @BeforeAll
    static void initPlatform() {
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    @BeforeEach
    void cleanScriptDir() throws Exception {
        for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.TEST)) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
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

    /** Test manager wiring: a real bridge that accepts listeners, the Counter and Trigger bindings,
     * and a real Graal engine. */
    private static final class ManagerHarness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final TestEventBridge bridge = new TestEventBridge();
        final Counter counter = new Counter();
        final Trigger trigger = new Trigger();
        final CancellationGate cancellation = new CancellationGate();
        final StubPluginRuntime pluginRuntime;
        final DefaultErrorTracker tracker;
        final NekoModulePipelineCache cache;
        final ScriptManager manager;
        final ScriptType scriptType;

        ManagerHarness(SandboxConfig config) {
            this(config, ScriptType.SERVER);
        }

        ManagerHarness(SandboxConfig config, ScriptType scriptType) {
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            this.pluginRuntime = new StubPluginRuntime(counter, bridge.group, trigger, cancellation);
            trigger.bind(bridge::postTestEvent);
            this.tracker = new DefaultErrorTracker(paths, config);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            this.cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory,
                        new com.tkisor.nekojs.core.state.GlobalStateStores());
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);
            this.bridge.manager = this.manager;
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

        void runTests() {
            manager.runTestScripts();
        }

        void reloadWithCancelledCandidate() {
            cancelCandidateDuring(this::reload);
        }

        void runTestsWithCancelledCandidate() {
            cancelCandidateDuring(this::runTests);
        }

        private void cancelCandidateDuring(Runnable operation) {
            AtomicReference<Throwable> cancellationFailure = new AtomicReference<>();
            Thread canceller = new Thread(() -> {
                try {
                    assertTrue(cancellation.ready.await(5, TimeUnit.SECONDS),
                            "candidate must reach the cancellation checkpoint");
                    Context owned = candidateContextOf(manager);
                    assertTrue(owned != null, "The candidate owner must retain its creator Context");
                    assertEquals(cancellation.context, owned, "The checkpoint must belong to this candidate");
                    owned.close(true);
                } catch (Throwable failure) {
                    cancellationFailure.set(failure);
                }
            }, "generation-test-candidate-canceller");
            canceller.setDaemon(true);
            canceller.start();
            try {
                operation.run();
            } finally {
                try {
                    canceller.join(5_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while joining the test canceller", interrupted);
                } finally {
                    if (canceller.isAlive()) canceller.interrupt();
                }
                assertTrue(!canceller.isAlive(), "test canceller must finish within its bounded join");
                assertTrue(cancellationFailure.get() == null,
                        () -> "External candidate cancellation failed: " + cancellationFailure.get());
            }
        }

        @Override
        public void close() {
            manager.close();
            engine.close();
        }
    }

    private static SandboxConfig testConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true);
    }

    /**
     * Successful reload: the scripts run once in the new generation and the markers record v1 -> v2.
     * After commit the same event is handled only by the new generation's listener, exactly once.
     */
    @Test
    void successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", """
                    Counter.hit('v1-entry');
                    TestEvents.ping(function (event) { Counter.hit('v1-listener'); });
                    """);
            harness.loadAndRun();
            assertEquals(1, harness.counter.hitsOf("v1-entry"));
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-listener"), "initial listener fires once");

            harness.writeScript("entry.js", """
                    Counter.hit('v2-entry');
                    TestEvents.ping(function (event) { Counter.hit('v2-listener'); });
                    """);
            harness.reload();

            assertEquals(1, harness.counter.hitsOf("v2-entry"), "reload must re-run the entry once");
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-listener"),
                    "post-commit event must not add to the old listener count (stays at initial 1)");
            assertEquals(1, harness.counter.hitsOf("v2-listener"), "new listener must receive the event exactly once");

            harness.bridge.postTestEvent();
            assertEquals(2, harness.counter.hitsOf("v2-listener"), "steady state: new listener keeps firing");
            assertEquals(1, harness.counter.hitsOf("v1-listener"), "old listener never fires again");
        }
    }

    /**
     * External cancellation of a candidate preserves the active Context, the script state and the
     * listeners.
     */
    @Test
    void failedReloadKeepsOldContextAndScriptState() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", """
                    Counter.hit('v1-entry');
                    TestEvents.ping(function (event) { Counter.hit('v1-listener'); });
                    """);
            harness.loadAndRun();
            assertEquals(1, harness.counter.hitsOf("v1-entry"));
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-listener"));

            Context oldContext = currentContext(harness.manager);
            harness.writeScript("entry.js", CANCELLED_CANDIDATE);
            RuntimeException failure = assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate,
                    "externally cancelled candidate must fail the reload");

            assertTrue(harness.pluginRuntime.closedBindings.isEmpty(),
                    "a failed candidate must not close live binding state");

            assertEquals(oldContext, currentContext(harness.manager),
                    "failed reload must keep the old context as current");
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-entry"),
                    "old entry must not re-run after failed reload");
            assertEquals(2, harness.counter.hitsOf("v1-listener"),
                    "active listener must keep receiving events after candidate failure (ticket 06 AC2)");
            // The failure is visible outside with its generation, phase, owner and domain; it carries no fix-up
            // hints
            assertInstanceOf(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class, failure);
            var report = ((com.tkisor.nekojs.core.lifecycle.NekoReloadException) failure).report();
            assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.EXECUTION, report.phase());
            assertTrue(report.generation() >= 1, "failure report must carry the candidate generation");
            assertEquals("ScriptManager[server]", report.owner());
            assertTrue(report.describe().contains("phase=EXECUTION"),
                    "describe() must expose the structured fields: " + report.describe());
        }
    }

    @Test
    void ordinaryCandidateExecutionFailuresKeepActiveContextAndListeners() throws Exception {
        for (String candidate : List.of("throw new Error('candidate failed');", "const invalid = ;")) {
            try (ManagerHarness harness = new ManagerHarness(testConfig())) {
                harness.writeScript("entry.js", """
                        globalThis.keptState = 'active';
                        Counter.hit('active-entry');
                        TestEvents.ping(event => Counter.hit('active-listener'));
                        """);
                harness.loadAndRun();
                Context active = currentContext(harness.manager);
                long generation = harness.manager.generationId();
                harness.writeScript("entry.js", candidate);

                var failure = assertThrows(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class,
                        harness::reload);
                assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.EXECUTION, failure.report().phase());
                assertEquals("script-execution", failure.report().domain());
                assertTrue(failure.report().sourceLocation().endsWith("entry.js"));
                assertSame(active, currentContext(harness.manager));
                assertEquals(generation, harness.manager.generationId());
                assertEquals("active", active.eval("js", "globalThis.keptState").asString());
                harness.bridge.postTestEvent();
                assertEquals(1, harness.counter.hitsOf("active-listener"));
                assertEquals(1, harness.counter.hitsOf("active-entry"));
                assertTrue(harness.pluginRuntime.closedBindings.isEmpty());
            }
        }
    }

    @Test
    void unreadableCandidateEntryKeepsActiveContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "globalThis.keptState = 'active';");
            harness.loadAndRun();
            Context active = currentContext(harness.manager);
            long generation = harness.manager.generationId();
            Files.write(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("entry.js"),
                    new byte[] { (byte) 0xC3, (byte) 0x28 });

            var failure = assertThrows(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class,
                    harness::reload);
            assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.PREPARATION, failure.report().phase());
            assertEquals("script-preload", failure.report().domain());
            assertSame(active, currentContext(harness.manager));
            assertEquals(generation, harness.manager.generationId());
            assertEquals("active", active.eval("js", "globalThis.keptState").asString());
        }
    }

    @Test
    void ordinaryClientCandidateFailureKeepsActiveScriptState() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig(), ScriptType.CLIENT)) {
            harness.writeScript("client-retention.js", "globalThis.keptState = 'typed input';");
            harness.loadAndRun();
            Context active = currentContext(harness.manager);
            long generation = harness.manager.generationId();
            harness.writeScript("client-retention.js", "throw new Error('candidate failed');");

            assertThrows(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class, harness::reload);
            assertSame(active, currentContext(harness.manager));
            assertEquals(generation, harness.manager.generationId());
            assertEquals("typed input", active.eval("js", "globalThis.keptState").asString());
        } finally {
            Files.deleteIfExists(ScriptTypeEnv.scriptsDir(ScriptType.CLIENT).resolve("client-retention.js"));
        }
    }

    /** Candidate preparation/virtual registration must not replace the active source-map session. */
    @Test
    void failedEsmReloadKeepsActiveSourceMapAndSuccessfulCommitPublishesNewSession() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            String oldSource = "throw new Error('old');\n";
            harness.writeScript("session-entry.mjs", oldSource);
            harness.loadAndRun();

            assertTrue(harness.tracker.getAllErrors().stream()
                    .anyMatch(error -> error.getDisplayPath().endsWith("session-entry.mjs")
                            && error.getErrorMessage().contains("old")),
                    "the active ESM failure must be visible before the candidate starts");
            assertEquals(oldSource, activeModuleSession(harness.manager).sourceMapView()
                    .getMappedPosition("server_scripts/session-entry.mjs", 1, 1).sourceContent);

            String candidateSource = "export const version = 'new';\n" + CANCELLED_CANDIDATE;
            harness.writeScript("session-entry.mjs", candidateSource);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);

            assertEquals(oldSource, activeModuleSession(harness.manager).sourceMapView()
                    .getMappedPosition("server_scripts/session-entry.mjs", 1, 1).sourceContent,
                    "candidate source maps must be discarded with the failed session");
            assertTrue(harness.tracker.getAllErrors().stream()
                    .anyMatch(error -> error.getDisplayPath().endsWith("session-entry.mjs")
                            && error.getErrorMessage().contains("old")),
                    "candidate errors must not replace the active same-id error");

            String committedSource = "export const version = 'committed';\n";
            harness.writeScript("session-entry.mjs", committedSource);
            harness.reload();
            assertEquals(committedSource, activeModuleSession(harness.manager).sourceMapView()
                    .getMappedPosition("server_scripts/session-entry.mjs", 1, 1).sourceContent,
                    "a committed candidate session must expose its new source map");
        }
    }

    /**
     * After a failed reload the active listeners are still on the bus: candidate listeners are only
     * collected, a failure discards them with the candidate, and the active production routing is
     * left alone.
     */
    @Test
    void failedReloadKeepsActiveListenersOnBus() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            assertTrue(harness.bridge.busHasListeners(), "listener registered on load");

            harness.writeScript("entry.js", CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);
            assertTrue(harness.bridge.busHasListeners(),
                    "candidate failure must not touch the active generation's listeners");
        }
    }

    /**
     * Candidate isolation: listeners registered by a failed reload never stay on the bus, because
     * they were only collected and never activated, and the active listeners are unaffected.
     */
    @Test
    void failedReloadDiscardsCandidateListenersOnly() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();

            // Collect the candidate listener before external cancellation discards the candidate.
            harness.writeScript("entry.js", """
                    TestEvents.ping(function (event) { Counter.hit('v2'); });
                    """ + CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);

            harness.bridge.postTestEvent();
            assertEquals(0, harness.counter.hitsOf("v2"),
                    "candidate listener must never fire: collected pre-commit, discarded with the failed candidate");
            assertEquals(1, harness.counter.hitsOf("v1"),
                    "active listener must still receive the event exactly once (no candidate residue)");
        }
    }

    /**
     * After commit the production timer dispatch belongs to one generation only: the old generation's
     * interval is cancelled at commit and the new generation's interval keeps being dispatched by
     * the production flush, so no tick runs twice.
     */
    @Test
    void afterCommitProductionTimerDispatchBelongsToNewGenerationOnly() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "setInterval(function () { Counter.hit('v1-timer'); }, 40);\n");
            harness.loadAndRun();
            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            int v1Before = harness.counter.hitsOf("v1-timer");
            assertTrue(v1Before >= 1, "old interval must be dispatched by production flush");

            harness.writeScript("entry.js", "setInterval(function () { Counter.hit('v2-timer'); }, 40);\n");
            harness.reload();

            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            int v1A = harness.counter.hitsOf("v1-timer");
            int v2A = harness.counter.hitsOf("v2-timer");
            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            int v1B = harness.counter.hitsOf("v1-timer");
            int v2B = harness.counter.hitsOf("v2-timer");
            assertTrue(v2A >= 1, "new interval must be dispatched after commit");
            assertTrue(v2B > v2A, "new interval keeps ticking (A=" + v2A + ", B=" + v2B + ")");
            assertEquals(v1A, v1B, "old interval must be cancelled at commit (A=" + v1A + ", B=" + v1B + ")");
        }
    }

    /**
     * Counts bindEvents and clearListeners over the lifecycle: the initial load binds once, a failed
     * reload never touches the bus because the sweep only happens at the commit point, a successful
     * reload sweeps exactly once, and afterwards only the new generation's listeners are on the bus
     * (no leftovers, no double registration).
     */
    @Test
    void bridgeLifecycleCallCounts() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            assertEquals(List.of(ScriptType.SERVER), harness.bridge.bindEventsCalls,
                    "initial load binds event groups exactly once");

            harness.writeScript("entry.js", CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);
            assertTrue(harness.bridge.clearListenersCalls.isEmpty(),
                    "failed reload must not clear any listeners (ticket 06: sweep happens only at commit)");

            // Successful reload: one full sweep at the commit point, then only the new generation's listeners
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v2'); });\n");
            harness.reload();
            assertEquals(List.of(ScriptType.SERVER), harness.bridge.clearListenersCalls,
                    "successful reload must sweep the old generation listeners exactly once at commit");
            assertTrue(harness.bridge.busHasListeners(), "new generation listeners are live after commit");
            harness.bridge.postTestEvent();
            assertEquals(0, harness.counter.hitsOf("v1"), "swept generation must not receive new events");
            assertEquals(1, harness.counter.hitsOf("v2"), "only the committed generation executes, exactly once");
        }
    }

    /**
     * While a candidate runs, real production events are still handled by the active listeners.
     * Candidate listeners, already registered in the candidate collector, must not receive
     * production events before commit.
     */
    @Test
    void candidatePhaseEventsServedByActiveGeneration() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();

            // Post a production event after collecting the candidate listener but before external
            // cancellation. Production routing must still belong to the active generation.
            harness.writeScript("entry.js", """
                    TestEvents.ping(function (event) { Counter.hit('v2'); });
                    Trigger.now();
                    """ + CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);

            assertTrue(harness.counter.hitsOf("v1") >= 1,
                    "production event during candidate phase must be served by the active listener (AC1)");
            assertEquals(0, harness.counter.hitsOf("v2"),
                    "candidate listener must not receive pre-commit production events (AC1)");
        }
    }

    /**
     * Consecutive commits: each generation executes once, the old Context is closed, and the
     * generation number increases monotonically.
     */
    @Test
    void consecutiveCommitsKeepSingleExecutionAndCloseOldContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            long generationAfterLoad = harness.manager.generationId();

            Context contextV1 = currentContext(harness.manager);
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v2'); });\n");
            harness.reload();
            Context contextV2 = currentContext(harness.manager);
            assertTrue(harness.manager.generationId() > generationAfterLoad,
                    "commit must advance the generation");

            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v3'); });\n");
            harness.reload();
            Context contextV3 = currentContext(harness.manager);
            assertTrue(harness.manager.generationId() > generationAfterLoad + 1,
                    "each commit advances the generation by one");

            harness.bridge.postTestEvent();
            assertEquals(0, harness.counter.hitsOf("v1"), "generation v1 swept");
            assertEquals(0, harness.counter.hitsOf("v2"), "generation v2 swept at the next commit");
            assertEquals(1, harness.counter.hitsOf("v3"), "only the newest generation executes, exactly once");

            // The old generation's Context was closed at commit, which is the observable side of the release
            assertThrows(Exception.class, () -> contextV1.eval("js", "1 + 1"),
                    "old generation context must be closed at commit");
            assertThrows(Exception.class, () -> contextV2.eval("js", "1 + 1"),
                    "previous generation context must be closed at the next commit");
            assertEquals("2", contextV3.eval("js", "String(1 + 1)").asString(),
                    "the committed generation context stays usable");
        }
    }

    /**
     * Commit side: a pending timer registered by the candidate is committed with the generation and
     * dispatched once by the production flush after commit. Timers registered while the candidate
     * runs only enter the candidate collector and never reach the production routing early.
     */
    @Test
    void pendingCandidateTimerCommittedWithGeneration() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "Counter.hit('v1-entry');\n");
            harness.loadAndRun();

            // Candidate script (.mjs with top-level await): registers a 200ms one-shot timer, which is still
            // pending at commit and is therefore only collected; awaiting a 0ms timer proves a timer
            // callback works on the candidate execution path (top-level await resumption)
            harness.writeScript("entry.mjs", """
                    Counter.hit('v2-entry');
                    setTimeout(function () { Counter.hit('v2-timer'); }, 200);
                    await new Promise(function (resolve) { setTimeout(resolve, 0); });
                    """);
            harness.reload();
            assertEquals(0, harness.counter.hitsOf("v2-timer"),
                    "pending timer must not fire during candidate execution or at the commit point itself");

            Thread.sleep(300);
            harness.manager.flushReadyNodeTimers();
            assertEquals(1, harness.counter.hitsOf("v2-timer"),
                    "pending timer committed with the generation is dispatched exactly once by production flush");
            harness.manager.flushReadyNodeTimers();
            assertEquals(1, harness.counter.hitsOf("v2-timer"),
                    "one-shot timer is not re-dispatched");
        }
    }

    /** Discard side: a pending timer of a failed candidate closes with the candidate and never
     * reaches production dispatch. */
    @Test
    void pendingCandidateTimerDiscardedWithFailedCandidate() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "setInterval(function () { Counter.hit('v1-timer'); }, 40);\n");
            harness.loadAndRun();
            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            int v1Before = harness.counter.hitsOf("v1-timer");
            assertTrue(v1Before >= 1, "active interval must be ticking before the failed reload");

            harness.writeScript("entry.js", """
                    setTimeout(function () { Counter.hit('v2-timer'); }, 200);
                    """ + CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate);

            Thread.sleep(300);
            harness.manager.flushReadyNodeTimers();
            assertEquals(0, harness.counter.hitsOf("v2-timer"),
                    "failed candidate's pending timer must be discarded with its generation");
            assertTrue(harness.counter.hitsOf("v1-timer") > v1Before,
                    "active generation's timer keeps being dispatched after candidate failure");
        }
    }

    /**
     * TEST side: candidate test callbacks for the TEST type run in the test harness. Candidate
     * listeners activate after commit, pending timers are flushed by flushTestTimers, and the old
     * TEST environment stays usable after a failed reload.
     */
    @Test
    void testTypeCandidateCallbacksRunInHarness() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig(), ScriptType.TEST)) {
            harness.writeScript("entry.js", """
                    Counter.hit('t1-entry');
                    TestEvents.ping(function (event) { Counter.hit('t1-listener'); });
                    setTimeout(function () { Counter.hit('t1-timer'); }, 0);
                    """);
            harness.runTests();
            assertEquals(1, harness.counter.hitsOf("t1-entry"));
            assertEquals(1, harness.counter.hitsOf("t1-timer"),
                    "candidate timer callback must run in the test harness after commit");

            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("t1-listener"),
                    "candidate listener must run in the test harness after commit");

            harness.writeScript("entry.js", CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::runTestsWithCancelledCandidate,
                    "externally cancelled candidate must fail the TEST run");
            harness.bridge.postTestEvent();
            assertEquals(2, harness.counter.hitsOf("t1-listener"),
                    "failed TEST candidate keeps the old TEST listeners alive");
            assertEquals(1, harness.counter.hitsOf("t1-timer"),
                    "old TEST timer count unchanged");
        }
    }

    /**
     * Regression for the non-rollback commit point: work done while candidate listeners activate,
     * such as the {@code Value.as(keyType)} conversion of a dispatch key (an illegal key throws),
     * must finish during the <strong>candidate phase</strong>.
     *
     * <p>If the conversion were deferred into the activation loop of {@code commitGeneration}, an
     * exception that is not a {@code NekoReloadException} would escape the outer catch, so
     * {@code discardCandidate} would not run while the old listeners were already swept and
     * {@code runtime} already pointed at the candidate: leaked candidate resources and a
     * half-activated generation.
     *
     * <p>This test asserts that an illegal key fails in the candidate EVENT_PLAN phase and takes the
     * discard path: the failure carries generation, phase, source location, owner and domain; the
     * candidate Context, timers and pending registrations are all closed; the active runtime,
     * scripts, generation and listeners remain usable.
     */
    @Test
    void illegalDispatchKeyFailsCandidatePlanAndPreservesActive() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js",
                    "TestEvents.ping(function (event) { Counter.hit('v1-listener'); });\n");
            harness.loadAndRun();
            Object activeRuntime = runtimeOf(harness.manager);
            Object activeScripts = scriptsOf(harness.manager);
            Context activeContext = currentContext(harness.manager);
            long activeGeneration = harness.manager.generationId();
            harness.bridge.postTestEvent();
            int activeHits = harness.counter.hitsOf("v1-listener");
            assertEquals(1, activeHits, "active listener must be live before the failing reload");

            // Capture the candidate Context while the candidate runs, so the assertions after the failure can
            // check that candidate resources were really closed; also post one production event to show
            // that the active generation still serves traffic during the candidate phase.
            AtomicReference<Context> candidateRef = new AtomicReference<>();
            harness.trigger.bind(() -> {
                candidateRef.set(candidateContextOf(harness.manager));
                harness.bridge.postTestEvent();
            });
            harness.writeScript("entry.js", """
                    Trigger.now();
                    setInterval(function () { Counter.hit('v2-timer'); }, 40);
                    TestEvents.lang({ bogus: true }, function (event) { Counter.hit('v2-listener'); });
                    """);

            RuntimeException failure = assertThrows(RuntimeException.class, harness::reload,
                    "an illegal dispatch key must fail the candidate plan, not half-activate the commit");
            assertInstanceOf(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class, failure,
                    "candidate-phase failure must be reported as a structured reload failure");
            var report = ((com.tkisor.nekojs.core.lifecycle.NekoReloadException) failure).report();
            assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.EVENT_PLAN, report.phase(),
                    "the key conversion must be moved to the candidate event-plan phase (review A1)");
            assertEquals("ScriptManager[server]", report.owner());
            assertEquals("candidate-listener-plan", report.domain());
            assertTrue(report.generation() >= activeGeneration + 1,
                    "failure report must carry the candidate generation: " + report.describe());
            assertTrue(report.sourceLocation() != null && report.sourceLocation().endsWith("entry.js"),
                    "failure report must point at the registering script: " + report.describe());
            assertEquals(activeHits + 1, harness.counter.hitsOf("v1-listener"),
                    "production event during candidate phase must be served by the active listener (AC1)");
            assertEquals(0, harness.counter.hitsOf("v2-listener"),
                    "candidate listener must never become live");

            // The active side is untouched: runtime, scripts, generation, Context and listeners are all
            // unchanged
            assertSame(activeRuntime, runtimeOf(harness.manager), "active runtime must be untouched");
            assertSame(activeScripts, scriptsOf(harness.manager), "active scripts must be untouched");
            assertEquals(activeGeneration, harness.manager.generationId(),
                    "a discarded candidate must not advance the generation");
            assertEquals(activeContext, currentContext(harness.manager), "active context must stay current");
            harness.bridge.postTestEvent();
            assertEquals(activeHits + 2, harness.counter.hitsOf("v1-listener"),
                    "active listener must keep receiving events after the discarded candidate");

            // All candidate resources are closed: the Context is closed and unregistered, pending registrations
            // are cleared, and candidate timers never reach production dispatch
            Context candidate = candidateRef.get();
            assertTrue(candidate != null,
                    "candidate context must have been observable during candidate execution");
            assertThrows(Exception.class, () -> candidate.eval("js", "1 + 1"),
                    "discarded candidate context must be closed");
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(candidate),
                    "discarded candidate context must be unregistered from CONTEXT_TO_MANAGER");
            assertEquals(0, pendingListenerCount(harness.manager),
                    "collected pending registrations must be discarded with the candidate");
            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            assertEquals(0, harness.counter.hitsOf("v2-timer"),
                    "candidate timer must never reach production dispatch");
        }
    }

    /**
     * An externally cancelled candidate must close its Context and unregister it from
     * {@code CONTEXT_TO_MANAGER}; keeping it in the active side alone would not rule out candidate
     * leaks.
     */
    @Test
    void killedCandidateContextIsClosedAndUnregistered() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "Counter.hit('v1-entry');\n");
            harness.loadAndRun();

            AtomicReference<Context> candidateRef = new AtomicReference<>();
            harness.trigger.bind(() -> candidateRef.set(candidateContextOf(harness.manager)));
            harness.writeScript("entry.js", """
                    Trigger.now();
                    """ + CANCELLED_CANDIDATE);
            assertThrows(RuntimeException.class, harness::reloadWithCancelledCandidate,
                    "externally cancelled candidate must fail the reload");

            Context candidate = candidateRef.get();
            assertTrue(candidate != null, "candidate context must be observable during candidate execution");
            assertThrows(Exception.class, () -> candidate.eval("js", "1 + 1"),
                    "discarded candidate context must be closed (review: explicit assertion was missing)");
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(candidate),
                    "discarded candidate context must be unregistered from CONTEXT_TO_MANAGER");
            assertEquals(0, pendingListenerCount(harness.manager),
                    "candidate bookkeeping must be cleared on discard");
        }
    }

    /**
     * Asserts the release order: the listener sweep at commit happens <strong>before</strong> the
     * candidate runtime is published, so the sweep still reads the old runtime. The bus therefore
     * never holds both generations at once, and the same event cannot run twice on old and new
     * listeners.
     *
     * <p>The release order of timer, Context and streams has no external observation point: it is
     * the code order inside {@code closeRuntimeResources}, and by then the old environment is
     * already detached from all production routing.
     */
    @Test
    void commitSweepsOldListenersBeforePublishingNewRuntime() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(testConfig())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            Object v1Runtime = runtimeOf(harness.manager);

            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v2'); });\n");
            harness.reload();

            assertEquals(1, harness.bridge.runtimeAtSweep.size(),
                    "a successful commit sweeps the old generation listeners exactly once");
            assertSame(v1Runtime, harness.bridge.runtimeAtSweep.get(0),
                    "the sweep must run while the OLD runtime is still published (before the candidate is)");
            assertTrue(runtimeOf(harness.manager) != v1Runtime,
                    "the candidate runtime must be published after the sweep");
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

    private static NekoModulePipelineCache activeModuleSession(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object environment = field.get(manager);
        Method sessionAccessor = environment.getClass().getDeclaredMethod("moduleSession");
        sessionAccessor.setAccessible(true);
        return (NekoModulePipelineCache) sessionAccessor.invoke(environment);
    }

    private static Object runtimeOf(ScriptManager manager) {
        try {
            Field field = ScriptManager.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return field.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object scriptsOf(ScriptManager manager) {
        try {
            Field field = ScriptManager.class.getDeclaredField("scripts");
            field.setAccessible(true);
            return field.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Context candidateContextOf(ScriptManager manager) {
        try {
            Field field = ScriptManager.class.getDeclaredField("candidateContext");
            field.setAccessible(true);
            return (Context) field.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int pendingListenerCount(ScriptManager manager) {
        try {
            Field field = ScriptManager.class.getDeclaredField("pendingListeners");
            field.setAccessible(true);
            return ((List<?>) field.get(manager)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
