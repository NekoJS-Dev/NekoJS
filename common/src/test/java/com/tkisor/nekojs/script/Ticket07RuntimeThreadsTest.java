package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
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
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies owner serialization, callback rejection, close preemption, and cancellation isolation.
 * Test-owned cancellation exercises failed-generation cleanup and explicit reload recovery.
 */
class Ticket07RuntimeThreadsTest {

    /** Listener event payload. */
    public static final class TestEvent {}

    /**
     * Script-visible test recorder: entry counts, listener and timer hits, and the spin condition used
     * when close preempts evaluation (a host call is a Graal safepoint, so close's interrupt breaks
     * candidate evaluation there). All lifecycle calls on the manager go through Java methods on this
     * recorder; ScriptManager itself is not exposed as a guest-visible binding, matching production.
     */
    public static final class Recorder {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger listenerHits = new AtomicInteger();
        final AtomicInteger timerHits = new AtomicInteger();
        final CountDownLatch firstSpin = new CountDownLatch(1);
        volatile String lastValue;
        /** Filled in by the harness: callbacks use it to request or query close state. */
        volatile ScriptManager manager;

        public void record(String value) {
            executions.incrementAndGet();
            lastValue = value;
        }

        public String value() {
            return lastValue;
        }

        public int count() {
            return executions.get();
        }

        public void hit() {
            listenerHits.incrementAndGet();
        }

        public int hits() {
            return listenerHits.get();
        }

        public void timerHit() {
            timerHits.incrementAndGet();
        }

        public int timerHits() {
            return timerHits.get();
        }

        private long spinStartedNanos;

        /** Provides interruption safepoints with a finite fallback if cancellation fails. */
        public boolean keepSpinning() {
            if (spinStartedNanos == 0L) {
                spinStartedNanos = System.nanoTime();
            }
            firstSpin.countDown();
            return System.nanoTime() - spinStartedNanos < TimeUnit.SECONDS.toNanos(10);
        }

        /** Captures the real Context that evaluation runs in. The kill report regression test needs the
         * old-generation Context reference. */
        volatile Context capturedContext;

        public void rememberContext(Value anyGuestValue) {
            this.capturedContext = anyGuestValue.getContext();
        }

        /** Spin condition: close has been requested. It stays false before close is issued, so the script
         * leaves the loop on its own. */
        public boolean closeRequested() {
            ScriptManager current = manager;
            return current != null && current.isCloseRequested();
        }

        /** Requests a reload from inside a callback and records the decision text. */
        public void requestReloadFromCallback() {
            ScriptManager current = manager;
            String decision = String.valueOf(current == null ? null : current.requestReload());
            record("reload-decision:" + decision);
        }

        /** Runs a throwing reload from inside a callback. The rejection surfaces as a structured failure, and
         * its domain is recorded. */
        public void reloadThrowingFromCallback() {
            try {
                manager.reloadScripts();
                record("threw:nothing");
            } catch (NekoReloadException e) {
                record("threw:" + e.report().domain());
            }
        }
    }

    /** Bridge that provides the real event bus (Ticket07Events.ping), so tests observe real dispatch. */
    static final class Ticket07EventBridge implements ScriptEventBridge {
        final EventGroup group = EventGroup.of("Ticket07Events");
        final EventBusJS<TestEvent, Void> pingBus;

        Ticket07EventBridge() {
            this.pingBus = group.server("ping", TestEvent.class);
        }

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindings.putMember("Ticket07Events", new EventGroupJS(group, type));
        }

        @Override
        public void clearListeners(ScriptType type) {
            group.clearListeners(type);
        }

        void postPing() {
            pingBus.post(new TestEvent());
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;
        private final EventGroup sharedGroup;

        StubPluginRuntime(Recorder recorder, EventGroup sharedGroup) {
            this.recorder = recorder;
            this.sharedGroup = sharedGroup;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", Binding.of("TestRecorder", recorder));
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return Map.of("Ticket07Events", sharedGroup);
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
    void cleanScriptDirs() throws Exception {
        for (Path dir : List.of(NekoJSPaths.get().serverScripts(), NekoJSPaths.get().clientScripts())) {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /** Test harness: a manager with the real bridge (Ticket07Events), Recorder bound in, and its own
     * Engine. */
    private static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final Ticket07EventBridge bridge = new Ticket07EventBridge();
        final Recorder recorder = new Recorder();
        final ScriptManager manager;
        final ScriptType scriptType;

        Harness(ScriptType scriptType, SandboxConfig config) {
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, bridge.group);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory,
                        new com.tkisor.nekojs.core.state.GlobalStateStores());
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);
            this.recorder.manager = this.manager;
        }

        void writeScript(String fileName, String source) throws Exception {
            Files.writeString(scriptsDir(scriptType).resolve(fileName), source);
        }

        void loadAndRun() {
            manager.discoverScripts();
            manager.loadScripts();
        }

        void reload() {
            manager.reloadScripts();
        }

        /** Cancels the captured evaluating context from a test-owned, bounded worker. */
        void cancelDuring(Runnable operation) throws Exception {
            ExecutorService canceller = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ticket07-context-canceller");
                thread.setDaemon(true);
                return thread;
            });
            Future<?> cancellation = canceller.submit(() -> {
                assertTrue(recorder.firstSpin.await(30, TimeUnit.SECONDS),
                        "evaluation must enter the bounded loop before cancellation");
                assertNotNull(recorder.capturedContext);
                Field candidate = ScriptManager.class.getDeclaredField("candidateContext");
                candidate.setAccessible(true);
                Context owned = (Context) candidate.get(manager);
                if (owned == null) {
                    Field active = ScriptManager.class.getDeclaredField("runtime");
                    active.setAccessible(true);
                    Object environment = active.get(manager);
                    Method context = environment.getClass().getDeclaredMethod("context");
                    context.setAccessible(true);
                    owned = (Context) context.invoke(environment);
                }
                assertEquals(recorder.capturedContext, owned, "The captured guest view must match its creator Context");
                owned.close(true);
                return null;
            });
            try {
                operation.run();
            } finally {
                try {
                    cancellation.get(30, TimeUnit.SECONDS);
                } finally {
                    canceller.shutdownNow();
                }
            }
        }

        @Override
        public void close() {
            try {
                manager.close();
            } catch (Throwable ignored) {
            }
            try {
                engine.close();
            } catch (Throwable ignored) {
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

    private static Path scriptsDir(ScriptType type) {
        return type == ScriptType.CLIENT
                ? NekoJSPaths.get().clientScripts()
                : NekoJSPaths.get().serverScripts();
    }

    /** Default configuration for owner, callback, and close-preemption checks. */
    private static SandboxConfig quietConfig() {
        return SandboxConfig.defaultConfig();
    }

    // ---- Concurrent reloads form a single sequence, with exactly one candidate per round ----

    @Test
    void concurrentReloadsAreSerializedWithSingleCandidate() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v');\n");
            h.loadAndRun();
            assertEquals(1, h.recorder.count());
            long base = h.manager.generationId();

            int reloads = 4;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(reloads);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < reloads; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        h.manager.reloadScripts();
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
                        try {
                            future.get(80, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } finally {
                pool.shutdownNow();
            }
            assertEquals(base + reloads, h.manager.generationId(),
                    "concurrent reloads must serialize: one candidate per reload, no overlap loss");
            assertEquals(1 + reloads, h.recorder.count(),
                    "every serialized reload must execute the entry exactly once (no double callback)");
        }
    }

    // ---- A reload inside a callback is rejected explicitly: no recursion, no waiting on itself ----

    @Test
    void reloadInsideManagedCallbackIsRejectedNotRecursive() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            // Requests a reload directly from a listener body. The EventBusJS dispatch point tracks callback depth,
            // so a lifecycle request on the same thread must be rejected explicitly instead of starting a
            // second candidate recursively.
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => {
                        TestRecorder.hit();
                        TestRecorder.requestReloadFromCallback();
                    });
                    TestRecorder.record('v1');
                    """);
            h.loadAndRun();
            long base = h.manager.generationId();

            h.bridge.postPing();
            assertEquals(1, h.recorder.hits(), "listener must run exactly once");
            assertEquals("reload-decision:REJECTED_REENTRANT", h.recorder.value(),
                    "callback-internal reload must be explicitly rejected (observable result)");
            assertEquals(base, h.manager.generationId(), "rejected reload must not create a candidate");

            // The throwing entry point also surfaces the rejection as a structured failure with an observable
            // domain, instead of deadlocking or recursing
            h.writeScript("entry2.js", """
                    Ticket07Events.ping(() => {
                        TestRecorder.hit();
                        TestRecorder.reloadThrowingFromCallback();
                    });
                    """);
            // entry2 registers its new listener only when the candidate runs; reload first so it takes effect
            // (listeners become active once the candidate commits)
            h.manager.reloadScripts();
            h.bridge.postPing();
            assertTrue(h.recorder.value() != null && h.recorder.value().startsWith("threw:"),
                    "throwing entry must surface the rejection, got: " + h.recorder.value());
            assertTrue(h.recorder.value().contains("REJECTED_REENTRANT"),
                    "rejection domain must be observable: " + h.recorder.value());
            assertEquals(base + 1, h.manager.generationId(), "only the outer explicit reload commits");

            // Outside a callback the same thread works normally again; queued or immediate execution both count
            assertEquals(ScriptLifecycleGate.Decision.EXECUTED, h.manager.requestReload());
            assertEquals(base + 2, h.manager.generationId());
        }
    }

    // ---- Non-owner (guest-created) threads go through the explicit scheduling entry ----

    @Test
    void nonOwnerThreadLifecycleIsQueuedThroughExplicitEntry() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v');\n");
            h.loadAndRun();
            long base = h.manager.generationId();

            ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ticket07-guest-thread");
                thread.setDaemon(true);
                return thread;
            });
            try {
                // A guest thread enters through the explicit scheduling entry, so it queues on the same lifecycle
                // sequence as the owner
                Future<Long> scheduled = pool.submit(() -> h.manager.scheduleOnOwner(() -> {
                    h.manager.reloadScripts();
                    return h.manager.generationId();
                }));
                assertEquals(base + 1, scheduled.get(60, TimeUnit.SECONDS).longValue(),
                        "scheduled reload must serialize onto the owner queue and commit");

                // A non-owner thread calling the public lifecycle method directly also queues on the instance lock
                // monitor. It does not bypass the queue or touch the Context concurrently; running queued is
                // the observable result.
                Future<?> direct = pool.submit(() -> {
                    h.manager.reloadScripts();
                    return null;
                });
                direct.get(60, TimeUnit.SECONDS);
                assertEquals(base + 2, h.manager.generationId());
                assertEquals(3, h.recorder.count(), "each queued reload executes exactly once");
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** Guest-facing side: advanced Java and thread capabilities are not tightened (guest JS creates Java
     * threads itself), and managed lifecycle only goes through the explicit entry. */
    @Test
    void guestThreadAdvancedCapabilitiesAreNotTightened() throws Exception {
        // allowThreads is on, so guest thread capabilities keep their current behavior; only the managed
        // lifecycle entry is restricted
        SandboxConfig config = new SandboxConfig(true, false, false, false, true, true, false, true);
        try (Harness h = new Harness(ScriptType.SERVER, config)) {
            // Guest JS really creates a Java thread here: the existing surface of allowCreateThread (the
            // ClassFilter THREAD_GROUP whitelist plus Java.type and start/join/currentThread interop).
            // Platform fact: a Context is single-threaded, so a thread created by guest code cannot
            // re-enter the Context to run a JS closure. That was never supported and is not restricted
            // here; this assertion pins thread creation and control themselves.
            h.writeScript("entry.js", """
                    const Thread = Java.type('java.lang.Thread');
                    const guest = new Thread();
                    guest.start();
                    guest.join();
                    TestRecorder.record('guest-created: alive=' + guest.isAlive()
                            + ', owner=' + Thread.currentThread().getName());
                    """);
            h.loadAndRun();
            assertEquals(1, h.recorder.count(), "script must complete on the owner thread");
            assertTrue(h.recorder.value().startsWith("guest-created: alive=false, owner="),
                    "guest JS must be able to create/start/join a Java thread and read back its state"
                        + " (actual: " + h.recorder.value() + ")");

            // A non-owner host thread reaches managed lifecycle through the explicit entry; guest code never
            // touches the Context on a bypass path
            h.writeScript("entry.js", "TestRecorder.record('reloaded-from-guest-scheduler');\n");
            final ScriptLifecycleGate.Decision[] decision = new ScriptLifecycleGate.Decision[1];
            Thread schedulingGuest = new Thread(() -> {
                try {
                    decision[0] = h.manager.scheduleOnOwner(h.manager::requestReload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "ticket07-guest-scheduler");
            schedulingGuest.setDaemon(true);
            schedulingGuest.start();
            schedulingGuest.join(60_000);
            assertFalse(schedulingGuest.isAlive(), "guest scheduling must complete without deadlock");
            assertEquals(ScriptLifecycleGate.Decision.EXECUTED, decision[0],
                    "scheduled requestReload must run to completion on the owner path");
            assertEquals("reloaded-from-guest-scheduler", h.recorder.value(),
                    "the scheduled reload must have actually executed the new script");
        }
    }

    /** Reverse guard: a kill report from a stale closure of the old generation must not mark a healthy
     * active generation as an isolated failure. */
    @Test
    void staleKillReportForOldGenerationContextDoesNotIsolateHealthyActive() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", """
                    TestRecorder.rememberContext({});
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('v1');
                    """);
            h.loadAndRun();
            Context oldContext = h.recorder.capturedContext;
            assertNotNull(oldContext, "guest side must capture the evaluating context");
            long generation = h.manager.generationId();
            int hits = h.recorder.hits();

            h.writeScript("entry.js", """
                    TestRecorder.rememberContext({});
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('v2');
                    """);
            h.reload();
            Context newContext = h.recorder.capturedContext;
            assertNotSame(oldContext, newContext, "reload must have swapped the active context");
            assertEquals(generation + 1, h.manager.generationId());
            assertFalse(h.manager.isActiveFailed(), "healthy reload must not be isolated");

            // A stale closure of the old generation, dispatched concurrently between the commit sweep and the bus
            // activation, reports a kill:
            h.manager.markContextKilled(oldContext);
            assertFalse(h.manager.isActiveFailed(),
                    "stale kill report for an old-generation context must not isolate the healthy active");
            assertEquals(generation + 1, h.manager.generationId(),
                    "stale kill report must not trigger any rebuild");
            h.bridge.postPing();
            assertEquals(hits + 1, h.recorder.hits(), "active listener must keep receiving events");

            // Control case: a real active kill still enters isolated failure; the main path is unaffected by
            // this fix
            h.manager.markContextKilled(newContext);
            assertTrue(h.manager.isActiveFailed(), "a real active kill must still enter isolated failure");
        }
    }

    // ---- close preempts an in-flight candidate (interrupt path), is idempotent, and rejects lifecycle
    // calls after it finishes ----

    @Test
    void closePreemptsInFlightCandidateViaInterrupt() throws Exception {
        // Only close interrupts the candidate; the bounded loop is a failure fallback.
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            assertEquals("v1", h.recorder.value());
            long base = h.manager.generationId();

            // Host-call safepoints let close interrupt the in-flight evaluation.
            h.writeScript("loop.js", "while (TestRecorder.keepSpinning()) {}\n");
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<NekoReloadException> reload = pool.submit(() -> {
                    try {
                        h.manager.reloadScripts();
                        return null;
                    } catch (NekoReloadException e) {
                        return e;
                    }
                });
                // close takes priority: set the request flag, interrupt the in-flight candidate, wait on the instance
                // lock for the owner to exit, then tear down
                assertTrue(h.recorder.firstSpin.await(30, TimeUnit.SECONDS),
                        "candidate evaluation must begin before close tests in-flight interruption");
                assertTimeoutPreemptively(Duration.ofSeconds(30), h.manager::close,
                        "close must preempt the in-flight candidate instead of hanging");
                assertTrue(h.manager.isClosed(), "close must complete after preemption");
                NekoReloadException failure = reload.get(30, TimeUnit.SECONDS);
                assertNotNull(failure, "in-flight reload must fail, never commit after close");
                Throwable interruption = failure.report().error();
                while (interruption != null && !(interruption instanceof PolyglotException polyglot
                        && polyglot.isInterrupted())) {
                    interruption = interruption.getCause();
                }
                assertNotNull(interruption,
                        "close must retain the actual Graal interruption in its module error cause chain");
                assertEquals("server/loop.js", failure.report().sourceLocation());
                assertEquals("close-preempted", failure.report().domain(),
                        "preemption must be observable: " + failure.report().describe());
                assertEquals(base, h.manager.generationId(), "preempted candidate must not commit");
                assertEquals("v1", h.recorder.value(), "active scripts must be untouched by preemption");

                // Idempotent: a repeated close does not throw, and once closed, new lifecycle calls are rejected
                // explicitly
                assertTimeoutPreemptively(Duration.ofSeconds(10), h.manager::close);
                NekoReloadException rejected = assertThrows(NekoReloadException.class, h.manager::reloadScripts);
                assertTrue(rejected.report().domain().contains("REJECTED_CLOSED"),
                        "reload after close must be rejected: " + rejected.report().describe());
                assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSED, h.manager.requestReload());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ---- close preempts an in-flight candidate: cancellation converges, scripts that have not started
    // stay unstarted, and the candidate never commits ----

    /**
     * The candidate script spins until close is requested and then exits on its own. Preemption shows up
     * at whichever cancellation point fires: an interrupt (phase=EXECUTION, candidate evaluation is
     * interrupted), the check between scripts (phase=EXECUTION, later scripts are not started), or the
     * check before commit (phase=COMMIT, the candidate finished but is discarded). A thread race cannot
     * tell the three apart, but the observable outcome is the same: domain=close-preempted, the
     * generation is unchanged, the candidate script that has not started (second.js) never runs, and the
     * candidate never commits.
     */
    @Test
    void closePreemptsRemainingCandidateScriptsAndNeverCommits() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            long base = h.manager.generationId();

            // gate spins until close is requested, then exits. second sorts after it by name and must not start
            // once a cancellation point takes effect
            h.writeScript("gate.js", "while (!TestRecorder.closeRequested() && TestRecorder.keepSpinning()) {}\nTestRecorder.record('gate-passed');\n");
            h.writeScript("second.js", "TestRecorder.record('second-ran');\n");
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<NekoReloadException> reload = pool.submit(() -> {
                    try {
                        h.manager.reloadScripts();
                        return null;
                    } catch (NekoReloadException e) {
                        return e;
                    }
                });
                assertTrue(h.recorder.firstSpin.await(30, TimeUnit.SECONDS),
                        "candidate evaluation must begin before close tests cancellation points");
                assertTimeoutPreemptively(Duration.ofSeconds(30), h.manager::close,
                        "close must preempt the candidate at a cancellation point");
                assertTrue(h.manager.isClosed());
                NekoReloadException failure = reload.get(30, TimeUnit.SECONDS);
                assertNotNull(failure, "reload must not commit after close was requested");
                assertEquals("close-preempted", failure.report().domain(),
                        "preemption must be observable regardless of which cancellation point won: "
                                + failure.report().describe());
                assertTrue(failure.report().phase() == ReloadPhase.EXECUTION
                                || failure.report().phase() == ReloadPhase.COMMIT,
                        "cancellation points live at execution or the commit boundary: "
                                + failure.report().describe());
                assertEquals(base, h.manager.generationId(), "preempted candidate must not commit");
                assertFalse("second-ran".equals(h.recorder.value()),
                        "not-yet-started candidate scripts must not run after close is requested");
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** close takes priority over a reload that has not started: after close is requested, new lifecycle
     * calls are rejected. */
    @Test
    void closeIsPrioritizedOverNotStartedReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            h.manager.close();
            assertTrue(h.manager.isCloseRequested());
            assertTrue(h.manager.isClosed());
            // reload/load that has not started is rejected at the gate, so queueing it will not run it either
            NekoReloadException reloadRejected = assertThrows(NekoReloadException.class, h.manager::reloadScripts);
            assertTrue(reloadRejected.report().domain().contains("REJECTED_CLOSED"));
            NekoReloadException loadRejected = assertThrows(NekoReloadException.class, h.manager::loadScripts);
            assertTrue(loadRejected.report().domain().contains("REJECTED_CLOSED"));
        }
    }

    // ---- Candidate cancellation retains active events, timers, and explicit recovery. ----

    @Test
    void ordinaryCandidateFailureRetainsScriptExecutionDomain() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            long base = h.manager.generationId();
            h.writeScript("entry.js", "throw new Error('candidate-failed');\n");
            NekoReloadException failure = assertThrows(NekoReloadException.class, h.manager::reloadScripts);
            assertEquals("script-execution", failure.report().domain());
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase());
            assertEquals(base, h.manager.generationId());
            assertFalse(h.manager.isCloseRequested());
            assertFalse(h.manager.isActiveFailed());
            assertEquals("v1", h.recorder.value());
        }
    }

    @Test
    void cancelledCandidateRetainsActiveAndRecoversByExplicitReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    setTimeout(() => TestRecorder.timerHit(), 0);
                    TestRecorder.record('v1');
                    """);
            h.loadAndRun();
            h.bridge.postPing();
            // The scheduling thread enqueues the ready callback of setTimeout(0) asynchronously, so poll flush until
            // it lands (capped at 1s, so a delayed scheduler wakeup cannot make the result flaky)
            for (int i = 0; i < 200 && h.recorder.timerHits() == 0; i++) {
                h.manager.flushReadyNodeTimers();
                Thread.sleep(5);
            }
            int hitsBefore = h.recorder.hits();
            int timerHitsBefore = h.recorder.timerHits();
            assertEquals(1, hitsBefore);
            assertEquals(1, timerHitsBefore);
            long base = h.manager.generationId();

            h.writeScript("loop.js", """
                    TestRecorder.rememberContext({});
                    while (TestRecorder.keepSpinning()) {}
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> h.cancelDuring(h.manager::reloadScripts));
            assertEquals("candidate-killed", failure.report().domain(),
                    "external cancellation without close must retain its termination domain");
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase(),
                    "external cancellation surfaces at execution: " + failure.report().describe());
            assertEquals(base, h.manager.generationId(), "failed candidate must retain the active generation");
            assertFalse(h.manager.isActiveFailed(), "candidate kill must not poison the active");

            // Everything on the active generation stays unchanged: listeners keep firing, timers do not change, and
            // the old state is still readable
            h.bridge.postPing();
            assertEquals(hitsBefore + 1, h.recorder.hits(), "active listener must keep receiving events");
            h.manager.flushReadyNodeTimers();
            assertEquals(timerHitsBefore, h.recorder.timerHits(), "no new active timers may appear from the candidate");
            assertEquals("v1", h.recorder.value(), "active script state must be untouched");

            // An explicit reload is the recovery entry: once the script is fixed, one successful reload commits a
            // new candidate
            Files.deleteIfExists(scriptsDir(ScriptType.SERVER).resolve("loop.js"));
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('v2');
                    """);
            h.manager.reloadScripts();
            assertEquals(base + 1, h.manager.generationId(), "explicit reload must be able to create a new candidate");
            assertEquals("v2", h.recorder.value());
            h.bridge.postPing();
            assertEquals(hitsBefore + 2, h.recorder.hits(), "reloaded listener must receive events exactly once per post");
        }
    }

    // ---- Active cancellation isolates failure until explicit reload. ----

    @Test
    void cancelledActiveEntersIsolatedFailureAndRecoversByExplicitReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            // Register the listener before externally cancelling active evaluation.
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('v1');
                    """);
            h.writeScript("loop.js", """
                    TestRecorder.rememberContext({});
                    while (TestRecorder.keepSpinning()) {}
                    """);
            h.manager.discoverScripts();
            h.cancelDuring(h.manager::loadScripts);
            long failedGeneration = h.manager.generationId();

            // Isolated failure is observable: the flag is set, and neither timer flush nor event dispatch rebuilds
            // or clears it
            assertTrue(h.manager.isActiveFailed(), "killed active must enter isolated failure");
            h.manager.flushReadyNodeTimers();
            assertEquals(failedGeneration, h.manager.generationId(),
                    "timer flush must not rebuild: no second active without explicit reload");
            assertTrue(h.manager.isActiveFailed(), "timer flush must not clear the isolated failure");

            // Dispatch to the killed Context stops: the listener closure points at a dead environment, so one post
            // no longer runs it
            h.bridge.postPing();
            assertEquals(0, h.recorder.hits(), "dispatch must stop reaching the killed active context");
            assertEquals(failedGeneration, h.manager.generationId(),
                    "event dispatch must not rebuild: no second active");

            // An explicit reload is the only recovery entry: after the script is fixed, a reload commits a new
            // generation and dispatch resumes
            Files.deleteIfExists(scriptsDir(ScriptType.SERVER).resolve("loop.js"));
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('recovered');
                    """);
            h.manager.reloadScripts();
            assertFalse(h.manager.isActiveFailed(), "explicit reload clears the isolated failure");
            assertEquals(failedGeneration + 1, h.manager.generationId());
            assertEquals("recovered", h.recorder.value());
            h.bridge.postPing();
            assertEquals(1, h.recorder.hits(), "recovered listener must receive events again");
        }
    }

    // ---- The SERVER and CLIENT owners are independent: concurrent reloads on their own owner threads do
    // not disturb each other ----

    @Test
    void serverAndClientOwnersReloadIndependently() throws Exception {
        // SERVER and CLIENT each use their own Engine, because Graal requires Contexts that share an Engine to
        // use the same HostAccess
        try (Harness server = new Harness(ScriptType.SERVER, quietConfig());
             Harness client = new Harness(ScriptType.CLIENT, quietConfig())) {
            server.writeScript("entry.js", "TestRecorder.record('server');\n");
            client.writeScript("entry.js", "TestRecorder.record('client');\n");
            server.loadAndRun();
            client.loadAndRun();
            long serverBase = server.manager.generationId();
            long clientBase = client.manager.generationId();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> serverReload = pool.submit(() -> {
                    server.manager.reloadScripts();
                    return null;
                });
                Future<?> clientReload = pool.submit(() -> {
                    client.manager.reloadScripts();
                    return null;
                });
                serverReload.get(60, TimeUnit.SECONDS);
                clientReload.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            assertEquals(serverBase + 1, server.manager.generationId());
            assertEquals(clientBase + 1, client.manager.generationId());
            assertEquals("server", server.recorder.value());
            assertEquals("client", client.recorder.value());
        }
    }

    // ---- The SERVER, CLIENT, STARTUP and TEST owner entries all reuse the existing root lifecycle entry ----

    @Test
    void fourOwnerEntriesReuseRootLifecycleGate() throws Exception {
        // NekoRuntimeRoot is the only runtime owner: load/reload/test for all four owners goes through the root
        // lifecycle entry into the same scheduling gate. After root close, lifecycle calls on all four
        // managers are rejected; there is no second manager or bypass path.
        Engine engine = Engine.newBuilder().build();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = quietConfig();
        Ticket07EventBridge bridge = new Ticket07EventBridge();
        Recorder recorder = new Recorder();
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, bridge.group);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                .newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, pluginRuntime, cache);
        NekoRuntimeRoot root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        try {
            Files.createDirectories(ScriptTypeEnv.scriptsDir(ScriptType.STARTUP));
            for (ScriptType type : ScriptType.values()) {
                root.createScriptManager(type).discoverScripts();
            }
            ScriptManager serverManager = root.scriptManagerOf(ScriptType.SERVER);
            ScriptManager testManager = root.scriptManagerOf(ScriptType.TEST);

            // All four owner entries run through the root lifecycle entry; an empty script directory still counts as
            // success
            assertTrue(root.reload(ScriptType.SERVER).success(), "SERVER reload through root entry");
            assertTrue(root.reload(ScriptType.CLIENT).success(), "CLIENT reload through root entry");
            NekoRuntimeRoot.ReloadResult startup = root.reload(ScriptType.STARTUP);
            assertTrue(startup.success() && startup.nonTransactional(),
                    "STARTUP reload through root entry keeps its explicit non-transactional boundary");
            assertNotNull(root.runTests(), "TEST run through root entry");

            // After root close, every lifecycle call on the same scheduling gate is rejected; no second recovery
            // path appears
            root.closeSilently();
            NekoReloadException rejected = assertThrows(NekoReloadException.class, serverManager::reloadScripts);
            assertTrue(rejected.report().domain().contains("REJECTED_CLOSED"),
                    "post-close reload must be rejected: " + rejected.report().describe());
            assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSED, testManager.requestReload());
        } finally {
            root.closeSilently();
            engine.close();
        }
    }
}
