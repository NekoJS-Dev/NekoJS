package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test: {@code /nekojs reload} must really take effect for helper modules that are
 * not entry points.
 *
 * <p>After a helper changes, a reload must re-execute every affected entry point so it observes
 * the new value; reporting success without doing that is the failure this test guards against.
 */
class ScriptReloadRegressionTest {

    /** Records the helper export value observed by an entry script; injected into the sandbox as a
     * global binding. */
    public static final class TestRecorder {
        private volatile String value;
        private volatile Context recordedContext;
        private volatile Context cancellationContext;
        private volatile CountDownLatch cancellationStarted = new CountDownLatch(1);
        private volatile boolean contextDead;
        private volatile Context deadContext;
        private volatile ScriptManager manager;
        private volatile boolean managerContextMatches;
        private volatile boolean managerKilled;
        private volatile Context managerContextAtProbe;
        /** The manager's candidate Context at probe time: it holds the candidate during the candidate
         * phase and is cleared after commit. */
        private volatile Context candidateContextAtProbe;
        /** Records each binding.close(scriptType) call, used to assert the teardown after a kill and
         * rebuild is equivalent. */
        final java.util.List<String> closedBindings = new CopyOnWriteArrayList<>();

        public void record(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        /** Records the Graal Context that owns the JS function calling this method, so tests can assert
         * which environment a script really ran in. */
        public void recordContext(Value fn) {
            this.recordedContext = fn.getContext();
        }

        public void recordCancellationContext(Value fn) {
            cancellationContext = fn.getContext();
            cancellationStarted.countDown();
        }

        public Context recordedContext() {
            return recordedContext;
        }

        /** Records whether {@code ScriptManager.isContextDead} reports the calling JS function's Context
         * as dead. */
        public void recordContextDead(Value fn) {
            Context context = fn.getContext();
            this.deadContext = context;
            this.contextDead = ScriptManager.isContextDead(context);
            if (manager != null) {
                try {
                    this.managerContextAtProbe = currentContext(manager);
                    this.managerContextMatches = (this.managerContextAtProbe != null
                            && this.managerContextAtProbe.equals(context));
                    Field killedField = ScriptManager.class.getDeclaredField("contextKilled");
                    killedField.setAccessible(true);
                    this.managerKilled = (boolean) killedField.get(manager);
                    Field candidateField = ScriptManager.class.getDeclaredField("candidateContext");
                    candidateField.setAccessible(true);
                    this.candidateContextAtProbe = (Context) candidateField.get(manager);
                } catch (Exception ignored) {
                    // Diagnostic helper: a reflection failure must not mask the original assertion
                }
            }
        }

        public boolean contextDead() {
            return contextDead;
        }

        public Context deadContext() {
            return deadContext;
        }

        public Context managerContextAtProbe() {
            return managerContextAtProbe;
        }

        /** The manager's candidate Context at probe time; {@code null} after commit. */
        public Context candidateContextAtProbe() {
            return candidateContextAtProbe;
        }

        public boolean managerContextMatches() {
            return managerContextMatches;
        }

        public boolean managerKilled() {
            return managerKilled;
        }

        public void bindManager(ScriptManager manager) {
            this.manager = manager;
        }
    }

    // Both limits belong to the fixture, not the production evaluator.
    private static final String CANCELLABLE_ENTRY = """
            TestRecorder.recordCancellationContext(() => {});
            const deadline = Date.now() + 10_000;
            for (let spin = 0; spin < 1_000_000_000 && Date.now() < deadline; spin++) {}
            TestRecorder.record('cancellation-missed');
            """;

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final TestRecorder recorder;

        StubPluginRuntime(TestRecorder recorder) {
            this.recorder = recorder;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", new Binding() {
                @Override public String name() { return "TestRecorder"; }
                @Override public Object value() { return recorder; }
                @Override public void close(ScriptType closedType) {
                    recorder.closedBindings.add(closedType.name());
                }
            });
        }

        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
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
        // Use a fixed, non-temporary directory: the NekoJSLoggers FileAppender holds a file handle on
        // <gameDir>/logs/nekojs/server.log until the JVM exits. If gameDir lived in a JUnit @TempDir,
        // cleaning up the temporary directory at class end would fail on Windows (executionError)
        // because the file is still in use.
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    /** Tests share one test gameDir: each test clears the script directory first, so tests do not
     * discover scripts left behind by another test. */
    @BeforeEach
    void cleanScriptDir() throws Exception {
        Path serverDir = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverDir);
        try (var stream = Files.list(serverDir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
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

    @Test
    void reloadingHelperModuleReRunsAffectedEntryWithNewCode() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.mjs");
        Path helper = serverDir.resolve("helper.mjs");

        Files.writeString(entry, """
                import { value } from './helper.mjs';
                TestRecorder.record(value);
                """);
        Files.writeString(helper, "export const value = 'v1';\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must execute the entry");

            Files.writeString(helper, "export const value = 'v2';\n");
            manager.reloadScriptFile("helper.mjs");

            assertEquals("v2", recorder.value(),
                    "helper reload must re-run the affected entry so the new export is observed");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * Single-file reload accepts three equivalent forms: the bare file name, {@code server/<file>}
     * (the id-style prefix), and {@code server_scripts/<file>} (the physical directory form, the path
     * users expect). The last form used to resolve to {@code <scripts>/server_scripts/<file>} and was
     * rejected.
     */
    @Test
    void singleFileReloadAcceptsBareIdAndScriptsDirPathForms() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("forms-entry.mjs");
        Files.writeString(entry, "TestRecorder.record('loaded');\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("loaded", recorder.value(), "initial load must execute the entry");

            manager.reloadScriptFile("forms-entry.mjs");
            manager.reloadScriptFile("server/forms-entry.mjs");
            manager.reloadScriptFile("server_scripts/forms-entry.mjs");
            assertEquals("loaded", recorder.value(), "every accepted path form must re-run the entry");

            ScriptManager activeManager = manager;
            IOException rejected = assertThrows(IOException.class,
                    () -> activeManager.reloadScriptFile("server_scripts/no_such_file.mjs"));
            assertTrue(rejected.getMessage().contains("Unsupported or missing script file"),
                    "missing files must keep the stable failure message");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
            Files.deleteIfExists(entry);
        }
    }

    /**
     * Regression: a transactional reload must load scripts in the candidate Context, not reuse the old
     * {@code this.context}/{@code this.nodeRuntime}.
     *
     * <p>The script calls {@code TestRecorder.recordContext((event) => {})} to report the Graal Context
     * that defined the function back to Java. After a successful reload, that Context must equal the
     * candidate Context the ScriptManager currently holds; before the fix the script ran in the old
     * Context, so the two differ.
     */
    @Test
    void transactionalReloadLoadsScriptsIntoCandidateContext() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.js");
        Files.writeString(entry, "TestRecorder.record('v1'); TestRecorder.recordContext((event) => {});\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must execute the entry");
            assertEquals(currentContext(manager), recorder.recordedContext(),
                    "initial load must run in the freshly created context");

            Files.writeString(entry, "TestRecorder.record('v2'); TestRecorder.recordContext((event) => {});\n");
            manager.reloadScripts();

            assertEquals("v2", recorder.value(), "reload must re-run the entry");
            assertEquals(currentContext(manager), recorder.recordedContext(),
                    "transactional reload must load candidate scripts into the candidate context");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * Regression: a successful full reload must clear {@code contextKilled}, and a failed candidate load
     * must restore the previous value. This test covers the success path; the failure path is restored by
     * the catch that handles candidate-kill detection.
     */
    @Test
    void successfulTransactionalReloadClearsContextKilledFlag() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.js");
        Files.writeString(entry, "TestRecorder.record('v1'); TestRecorder.recordContext((event) => {});\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            setContextKilled(manager, true);

            Files.writeString(entry, "TestRecorder.record('v2'); TestRecorder.recordContext((event) => {});\n");
            manager.reloadScripts();

            assertFalse(isContextKilled(manager),
                    "a successfully reloaded context is healthy, so contextKilled must be false");
            assertEquals("v2", recorder.value(), "reload must re-run the entry");
            assertEquals(currentContext(manager), recorder.recordedContext(),
                    "candidate scripts must run in the candidate context");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * Regression: a script whose preload read fails (bad encoding, permission problem) must not vanish
     * silently. The old behavior set {@code disabled=true}, {@code shouldRun()} skipped the script, and
     * nothing read {@code lastError}, so neither the log nor the error panel showed a trace. It must now
     * reach the ErrorTracker with the exception type preserved.
     */
    @Test
    void unreadableScriptSurfacesInErrorTrackerInsteadOfVanishing() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path broken = serverDir.resolve("broken-encoding.js");
        // 0x80/0x81 is not a valid UTF-8 sequence: the default UTF-8 decoder in Files.newBufferedReader
        // throws MalformedInputException when it reads it, which exercises preload's catch(Exception) path
        Files.write(broken, new byte[]{(byte) 0x80, (byte) 0x81, '\n'});

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            manager.discoverScripts();
            assertDoesNotThrow(manager::loadScripts,
                    "an unreadable sibling script must not abort the whole load");

            boolean reported = tracker.getAllErrors().stream()
                    .anyMatch(error -> error.getErrorId() != null
                            && error.getErrorId().toString().contains("broken-encoding"));
            assertTrue(reported,
                    "unreadable script must land in the error tracker instead of vanishing; errors="
                            + tracker.getAllErrors().stream().map(e -> e.getErrorId()).toList());
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
            Files.deleteIfExists(broken);
        }
    }

    /** A cancelled candidate must roll back even when the previous active was already marked failed. */
    @Test
    void transactionalReloadRejectsCancelledCandidateWhenPreviousKilledIsTrue() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.js");
        Files.writeString(entry, "TestRecorder.record('v1');\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must succeed");

            Context oldContext = currentContext(manager);
            setContextKilled(manager, true);

            Files.writeString(entry, CANCELLABLE_ENTRY);
            ScriptManager reloadManager = manager;
            assertThrows(RuntimeException.class,
                    () -> runWithCancelledContext(recorder, reloadManager, reloadManager::reloadScripts),
                    "reload must fail when the candidate context is externally cancelled");
            assertTrue(isContextKilled(manager),
                    "failure path must restore previousKilled=true instead of committing the dead candidate");
            assertEquals(oldContext, currentContext(manager),
                    "failed transactional reload must keep the old context as current");
            assertNotEquals(oldContext, recorder.cancellationContext,
                    "the cancellation probe must capture the candidate, not the old active context");
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(recorder.cancellationContext),
                    "Discarded candidate must be unregistered from callback routing");
            assertThrows(Exception.class, () -> recorder.cancellationContext.eval("js", "1 + 1"),
                    "Discarded candidate must be closed, not merely detached");
            assertEquals(1, contextCountFor(manager),
                    "failed candidate cleanup must retain only the previous active context");
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * Regression: while a transactional reload loads candidates, timer callbacks on the candidate
     * Context must not be treated as dead. Before the fix {@code isContextDead(candidateContext)} saw
     * the old Context until the final swap, so the callback of
     * {@code await new Promise(r => setTimeout(r, 0))} was skipped and the candidate load timed out.
     */
    @Test
    void transactionalReloadCandidateTimerCallbacksAreNotSkipped() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.mjs");
        Files.writeString(entry, "TestRecorder.record('v1');\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            recorder.bindManager(manager);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must succeed");

            Files.writeString(entry, """
                    TestRecorder.recordContextDead((event) => {});
                    TestRecorder.record('v2-start');
                    await new Promise(resolve => setTimeout(() => {
                        TestRecorder.record('v2-timer-fired');
                        resolve();
                    }, 0));
                    TestRecorder.record('v2-end');
                    """);
            runBounded(manager::reloadScripts, () -> {});
            assertEquals(currentContext(manager), recorder.deadContext(),
                    "isContextDead probe must run in the candidate context");
            // Candidates are no longer published as the live environment early (generation isolation), so the
            // diagnostic assertion follows that rule: during candidate load the probe context is the
            // manager's candidateContext, not runtime.context().
            assertTrue(recorder.candidateContextAtProbe() != null
                            && recorder.candidateContextAtProbe().equals(recorder.deadContext()),
                    "diagnostic: probe context must be the manager's candidate context during candidate load; "
                            + "probe=" + recorder.deadContext()
                            + " candidateAtProbe=" + recorder.candidateContextAtProbe()
                            + " managerAtProbe=" + recorder.managerContextAtProbe()
                            + " contextDead=" + recorder.contextDead() + " managerKilled=" + recorder.managerKilled());
            assertFalse(recorder.managerKilled(),
                    "diagnostic: contextKilled should be false during candidate load");
            assertFalse(recorder.contextDead(),
                    "candidate context must not be considered dead during candidate script loading");
            assertEquals("v2-end", recorder.value(),
                    "candidate script must run to completion, including its timer-resumed continuation");
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * Regression test: the check-then-act in getOrCreateContext must be atomic.
     *
     * <p>Before the fix the method was unsynchronized, so threads that passed the null check at the same
     * time each created a Graal Context, and the losing Context (with its Node timer scheduling thread)
     * leaked in {@link ScriptManager#CONTEXT_TO_MANAGER}. The only public API that runs without the
     * instance lock, {@link ScriptManager#flushReadyNodeTimers()}, does not create a Context, so the test
     * calls the private getOrCreateContext concurrently through reflection (existing tests here also use
     * reflection) and pins the rule: a fresh manager leaves exactly one Context entry.
     */
    @Test
    void concurrentGetOrCreateContextCreatesSingleContextPerManager() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        TestRecorder recorder = new TestRecorder();
        Method getOrCreateContext = ScriptManager.class.getDeclaredMethod("getOrCreateContext");
        getOrCreateContext.setAccessible(true);

        int threads = 8;
        int rounds = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                // Each round uses its own Engine: Graal requires every Context sharing an Engine to use the same
                // HostAccess configuration, and newManager builds a new NekoSharedHostAccess each time.
                Engine engine = Engine.newBuilder().build();
                ScriptManager manager = newManager(paths, recorder, engine);
                try {
                    CountDownLatch start = new CountDownLatch(1);
                    List<Future<Context>> futures = new ArrayList<>();
                    for (int t = 0; t < threads; t++) {
                        futures.add(pool.submit(() -> {
                            start.await();
                            return (Context) getOrCreateContext.invoke(manager);
                        }));
                    }
                    start.countDown();
                    for (Future<Context> future : futures) {
                        assertDoesNotThrow(() -> future.get(60, TimeUnit.SECONDS));
                    }
                    int contexts = contextCountFor(manager);
                    assertEquals(1, contexts,
                            "round " + round + ": concurrent getOrCreateContext must leave exactly one Context in CONTEXT_TO_MANAGER");
                } finally {
                    manager.close();
                    engine.close();
                }
            }
        } finally {
            forceCloseLiveContexts();
            pool.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static int contextCountFor(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
        field.setAccessible(true);
        Map<Context, ScriptManager> map = (Map<Context, ScriptManager>) field.get(null);
        int count = 0;
        for (ScriptManager value : map.values()) {
            if (value == manager) {
                count++;
            }
        }
        return count;
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine) {
        return newManager(paths, recorder, engine, SandboxConfig.defaultConfig());
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine, SandboxConfig config) {
        return newManager(paths, recorder, engine, config, new DefaultErrorTracker(paths, config));
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine,
                                            SandboxConfig config, DefaultErrorTracker tracker) {
        return newManager(paths, recorder, engine, config, tracker, ScriptEventBridge.EMPTY);
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine,
                                            SandboxConfig config, DefaultErrorTracker tracker,
                                            ScriptEventBridge eventBridge) {
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                .newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory,
                        new com.tkisor.nekojs.core.state.GlobalStateStores());
        return new ScriptManager(ScriptType.SERVER, eventBridge, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);
    }

    private static Context currentContext(ScriptManager manager) throws Exception {
        // context, nodeRuntime and the streams now live in the RuntimeEnvironment record, published as a pair
        // through one volatile reference; reading context here is equivalent to reading the old field
        // directly
        Field field = ScriptManager.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object environment = field.get(manager);
        Method contextAccessor = environment.getClass().getDeclaredMethod("context");
        contextAccessor.setAccessible(true);
        return (Context) contextAccessor.invoke(environment);
    }

    @SuppressWarnings("unchecked")
    private static boolean isContextKilled(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("contextKilled");
        field.setAccessible(true);
        return (boolean) field.get(manager);
    }

    private static void setContextKilled(ScriptManager manager, boolean value) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("contextKilled");
        field.setAccessible(true);
        field.set(manager, value);
    }

    /** External cancellation must release the lifecycle owner and require explicit recovery. */
    @Test
    void cancelledScriptEntryReleasesOwnerAndAllowsExplicitRecovery() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("loop.js");
        Files.writeString(entry, CANCELLABLE_ENTRY);

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = newManager(paths, recorder, engine, config, tracker);
        try {
            manager.discoverScripts();
            runWithCancelledContext(recorder, manager, manager::loadScripts);
            assertTrue(tracker.hasErrors(), "external cancellation must be recorded");
            assertTrue(isContextKilled(manager), "cancelled active must remain marked failed");
            Context cancelled = currentContext(manager);
            manager.flushReadyNodeTimers();
            assertEquals(cancelled, currentContext(manager),
                    "timer dispatch must not silently replace the failed active context");
            assertTrue(isContextKilled(manager), "timer dispatch must not clear the recovery gate");

            Files.writeString(entry, "TestRecorder.record('recovered');\n");
            manager.discoverScripts();
            runBounded(manager::loadScripts, () -> {});
            assertEquals("recovered", recorder.value(), "explicit load must execute healthy replacement code");
            assertNotEquals(cancelled, currentContext(manager), "explicit recovery must create a fresh context");
            assertFalse(isContextKilled(manager), "successful explicit recovery must clear the failure gate");
        } finally {
            forceCloseLiveContexts();
            manager.close();
            engine.close();
        }
    }

    /** Explicit recovery must remove listener closures belonging to the cancelled active context. */
    @Test
    void rebuildAfterExternalCancellationClearsEventListeners() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("loop.js");
        Files.writeString(entry, CANCELLABLE_ENTRY);

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        RecordingEventBridge bridge = new RecordingEventBridge();
        ScriptManager manager = newManager(paths, recorder, engine, config, tracker, bridge);
        try {
            manager.discoverScripts();
            runWithCancelledContext(recorder, manager, manager::loadScripts);
            assertTrue(isContextKilled(manager), "initial load must mark the cancelled active failed");
            assertTrue(bridge.typeOnlyCleared.isEmpty(),
                    "first (fresh) environment creation must not clear listeners");

            Files.writeString(entry, "TestRecorder.record('recovered');\n");
            runBounded(() -> manager.reloadScriptFile("loop.js"), () -> {});
            assertTrue(bridge.typeOnlyCleared.contains(ScriptType.SERVER),
                    "context rebuild after cancellation must clear this scriptType's listeners");
            assertEquals("recovered", recorder.value(), "single-file recovery must execute the replacement code");
            assertFalse(isContextKilled(manager), "single-file recovery must clear the failure gate");
        } finally {
            forceCloseLiveContexts();
            manager.close();
            engine.close();
        }
    }

    /** Cancelled-context recovery must match normal teardown for errors, bindings, and listeners. */
    @Test
    void rebuildAfterExternalCancellationMatchesFullTeardown() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("loop.js");
        Files.writeString(entry, "TestRecorder.record('initial');\n");
        Files.writeString(serverDir.resolve("broken.js"), "this is ! not valid javascript\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        RecordingEventBridge bridge = new RecordingEventBridge();
        ScriptManager manager = newManager(paths, recorder, engine, config, tracker, bridge);
        try {
            manager.discoverScripts();
            manager.loadScripts();
            assertTrue(tracker.getAllErrors().stream()
                            .anyMatch(error -> String.valueOf(error.getErrorId()).contains("broken")),
                    "broken.js must be tracked before cancellation");

            Files.writeString(entry, CANCELLABLE_ENTRY);
            runWithCancelledContext(recorder, manager, () -> manager.reloadScriptFile("loop.js"));
            assertTrue(isContextKilled(manager), "single-file execution must mark the cancelled active failed");
            assertTrue(tracker.getAllErrors().stream()
                            .anyMatch(error -> String.valueOf(error.getErrorId()).contains("broken")),
                    "the unrelated error must still exist immediately before recovery");

            Files.writeString(entry, "TestRecorder.record('recovered');\n");
            runBounded(() -> manager.reloadScriptFile("loop.js"), () -> {});
            assertTrue(bridge.typeOnlyCleared.contains(ScriptType.SERVER),
                    "rebuild must clear this scriptType's listeners");
            assertTrue(recorder.closedBindings.contains(ScriptType.SERVER.name()),
                    "rebuild must close this scriptType's bindings (teardown parity with resetEnvironment/close)");
            boolean brokenStillTracked = tracker.getAllErrors().stream()
                    .anyMatch(error -> String.valueOf(error.getErrorId()).contains("broken"));
            assertFalse(brokenStillTracked,
                    "rebuild must clear this scriptType's error tracker entries via fullReloadCleanup; errors="
                            + tracker.getAllErrors().stream().map(e -> e.getErrorId()).toList());
            assertEquals("recovered", recorder.value(), "recovery must execute healthy code");
            assertFalse(isContextKilled(manager), "full teardown recovery must clear the failure gate");
        } finally {
            forceCloseLiveContexts();
            manager.close();
            engine.close();
            Files.deleteIfExists(serverDir.resolve("broken.js"));
            Files.deleteIfExists(entry);
        }
    }

    @FunctionalInterface
    private interface TestOperation {
        void run() throws Exception;
    }

    private static void runWithCancelledContext(TestRecorder recorder, ScriptManager manager, TestOperation operation) throws Exception {
        recorder.cancellationContext = null;
        recorder.cancellationStarted = new CountDownLatch(1);
        runBounded(operation, () -> {
            assertTrue(recorder.cancellationStarted.await(10, TimeUnit.SECONDS),
                    "guest execution must reach the cancellation probe");
            Field candidate = ScriptManager.class.getDeclaredField("candidateContext");
            candidate.setAccessible(true);
            Context owned = (Context) candidate.get(manager);
            if (owned == null) owned = currentContext(manager);
            assertEquals(recorder.cancellationContext, owned, "The checkpoint must identify the owned evaluating Context");
            recorder.cancellationContext = owned;
            owned.close(true);
        });
    }

    /** Interrupt pending module waits on timeout and always join the test-owned lifecycle worker. */
    private static void runBounded(TestOperation operation, TestOperation whileRunning) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "nekojs-reload-regression");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> execution = worker.submit(() -> {
            operation.run();
            return null;
        });
        try {
            whileRunning.run();
            try {
                execution.get(20, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof Exception cause) {
                    throw cause;
                }
                if (failure.getCause() instanceof Error cause) {
                    throw cause;
                }
                throw new AssertionError(failure.getCause());
            }
        } finally {
            execution.cancel(true);
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(20, TimeUnit.SECONDS),
                    "test-owned lifecycle worker must terminate before context cleanup");
        }
    }

    /** Records clearListeners calls. It distinguishes a whole-type clear (the automatic rebuild path after
     * a kill) from a per-script clear (the single-file reload path). */
    private static final class RecordingEventBridge implements ScriptEventBridge {
        final List<ScriptType> typeOnlyCleared = new CopyOnWriteArrayList<>();
        final List<String> scriptScopedCleared = new CopyOnWriteArrayList<>();

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
        }

        @Override
        public void clearListeners(ScriptType type) {
            typeOnlyCleared.add(type);
        }

        @Override
        public void clearListeners(ScriptType type, String scriptId) {
            scriptScopedCleared.add(type + ":" + scriptId);
        }
    }

    @SuppressWarnings("unchecked")
    private static void forceCloseLiveContexts() {
        try {
            Field field = ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
            field.setAccessible(true);
            Map<Context, ScriptManager> map = (Map<Context, ScriptManager>) field.get(null);
            for (Context context : List.copyOf(map.keySet())) {
                try {
                    context.close(true);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
