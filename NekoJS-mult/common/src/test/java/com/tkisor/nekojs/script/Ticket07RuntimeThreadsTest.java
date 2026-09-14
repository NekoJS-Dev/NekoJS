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
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 fixture：同类型串行、回调内拒绝、close 优先、非 owner 调度、
 * 候选/active watchdog 隔离恢复。覆盖 SERVER 与 CLIENT owner、至少一个非 owner
 * 线程与 close 抢占路径；全部断言走公开 seam（generation、requestReload 判决、
 * 失败报告 domain、isActiveFailed/isClosed），不把私有锁当契约。
 */
class Ticket07RuntimeThreadsTest {

    /** 脚本可见的测试记录器：计数、阻塞门（close 抢占的确定性慢候选）。 */
    public static final class Recorder {
        final AtomicInteger executions = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        public void record(String value) {
            executions.incrementAndGet();
            lastValue = value;
        }

        volatile String lastValue;

        public String value() {
            return lastValue;
        }

        public int count() {
            return executions.get();
        }

        /** 慢候选脚本调用：在 close 发起前阻塞 owner 线程，确定性制造在途 candidate。 */
        public void awaitGate() throws InterruptedException {
            entered.countDown();
            release.await(20, TimeUnit.SECONDS);
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;

        StubPluginRuntime(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", new Binding() {
                @Override public String name() { return "TestRecorder"; }
                @Override public Object value() { return recorder; }
                @Override public void close(ScriptType closedType) { }
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

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static ScriptManager newManager(ScriptType type, NekoJSPaths paths, Recorder recorder,
                                            Engine engine, SandboxConfig config,
                                            DefaultErrorTracker tracker) {
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory);
        return new ScriptManager(type, ScriptEventBridge.EMPTY, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory);
    }

    private static Path scriptsDir(ScriptType type) {
        return type == ScriptType.CLIENT
                ? NekoJSPaths.get().clientScripts()
                : NekoJSPaths.get().serverScripts();
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

    @Test
    void concurrentReloadsAreSerializedWithSingleCandidate() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptManager manager = null;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals(1, recorder.count());
            long base = manager.generationId();

            int reloads = 4;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            ScriptManager finalManager = manager;
            for (int i = 0; i < reloads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    finalManager.reloadScripts();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                    try {
                        future.get(55, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            assertEquals(base + reloads, manager.generationId(),
                    "concurrent reloads must serialize: one candidate per reload, no overlap loss");
            assertEquals(1 + reloads, recorder.count(),
                    "every serialized reload must execute the entry exactly once (no double callback)");
        } finally {
            pool.shutdownNow();
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void reloadInsideManagedCallbackIsRejectedNotRecursive() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptManager manager = null;
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            long base = manager.generationId();
            ScriptManager finalManager = manager;

            // managed 回调内（事件监听器 / timer 回调执行体走同一 seam）：
            // reload 不得递归进自身队列，必须返回明确拒绝。
            ScriptManager.noteCallbackEnter();
            try {
                assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                        finalManager.requestReload(),
                        "callback-internal reload must be explicitly rejected, not queued on self");
                NekoReloadException failure = assertThrows(NekoReloadException.class,
                        finalManager::reloadScripts,
                        "throwing entry must surface the rejection as a structured failure");
                assertTrue(failure.report().domain().contains("REJECTED_REENTRANT"),
                        "rejection domain must be observable: " + failure.report().describe());
            } finally {
                ScriptManager.noteCallbackExit();
            }
            assertEquals(base, manager.generationId(), "rejected reload must not create a candidate");

            // 回调外同一线程恢复正常。
            assertEquals(ScriptLifecycleGate.Decision.EXECUTED, manager.requestReload());
            assertEquals(base + 1, manager.generationId());
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void nonOwnerThreadLifecycleIsQueuedThroughExplicitEntry() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptManager manager = null;
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ticket07-guest-thread");
            thread.setDaemon(true);
            return thread;
        });
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            long base = manager.generationId();
            ScriptManager finalManager = manager;

            // guest 线程经显式调度入口进入：排队串行，不直接触碰 Context。
            Future<String> scheduled = pool.submit(() -> finalManager.scheduleOnOwner(() -> {
                finalManager.reloadScripts();
                return "reloaded";
            }));
            assertEquals("reloaded", scheduled.get(60, TimeUnit.SECONDS));
            assertEquals(base + 1, manager.generationId());

            // 非 owner 直接调用同样经实例锁排队成功（不旁路、不并发触碰 Context）。
            Future<?> direct = pool.submit(() -> {
                finalManager.reloadScripts();
                return null;
            });
            direct.get(60, TimeUnit.SECONDS);
            assertEquals(base + 2, manager.generationId());
            assertEquals(1 + 2, recorder.count());
        } finally {
            pool.shutdownNow();
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void closePreemptsInflightCandidateAndIsIdempotent() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        // 初载用健康脚本；在途慢候选只出现在 reload 阶段。
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v1');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptManager manager = null;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value());
            long base = manager.generationId();
            ScriptManager finalManager = manager;

            // reload 候选写入慢脚本：在途 candidate 确定性阻塞在脚本执行中。
            Files.writeString(dir.resolve("slow.js"),
                    "TestRecorder.awaitGate();\nTestRecorder.record('slow');\n");
            Future<NekoReloadException> reload = pool.submit(() -> {
                try {
                    finalManager.reloadScripts();
                    return null;
                } catch (NekoReloadException e) {
                    return e;
                }
            });
            assertTrue(recorder.entered.await(30, TimeUnit.SECONDS),
                    "candidate must reach the blocking script before close preempts it");
            // close 优先：在途 candidate 被丢弃，不提交。
            finalManager.close();
            assertTrue(finalManager.isClosed());
            // 放行 owner 线程，让它走完丢弃路径（若仍卡住则 20s 门限超时失败）。
            recorder.release.countDown();
            NekoReloadException failure = reload.get(60, TimeUnit.SECONDS);
            assertTrue(failure != null, "in-flight reload must fail, never commit after close");
            assertEquals("close-preempted", failure.report().domain(),
                    "preemption must be observable: " + failure.report().describe());
            assertEquals(base, finalManager.generationId(), "preempted candidate must not commit");

            // 幂等：重复 close 不抛；已关闭后新 lifecycle 拒绝。
            finalManager.close();
            NekoReloadException rejected = assertThrows(NekoReloadException.class,
                    finalManager::reloadScripts);
            assertTrue(rejected.report().domain().contains("REJECTED_CLOSED"),
                    "reload after close must be rejected: " + rejected.report().describe());
            assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSED, finalManager.requestReload());
        } finally {
            recorder.release.countDown();
            pool.shutdownNow();
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void candidateWatchdogRetainsActiveAndRecoversByExplicitReload() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v1');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        // 时间窗口路径（语句上限关闭）：空循环无语句可数，只能靠墙钟中断。
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true,
                30, 0L, 2);
        ScriptManager manager = null;
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value());
            long base = manager.generationId();

            Files.writeString(dir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");
            ScriptManager finalRef = manager;
            NekoReloadException failure = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                try {
                    finalRef.reloadScripts();
                    return null;
                } catch (NekoReloadException e) {
                    return e;
                }
            }, "watchdog (2s) must abort the runaway candidate within the budget");
            assertTrue(failure != null, "runaway candidate must fail, never commit");
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase(),
                    "watchdog kill surfaces at execution: " + failure.report().describe());
            assertEquals(base, manager.generationId(), "failed candidate must retain the active generation");
            assertEquals("v1", recorder.value(), "active scripts must be untouched by the runaway candidate");
            assertFalse(manager.isActiveFailed(), "candidate kill must not poison the active");

            // 显式 reload 是恢复入口：修好脚本后一次成功 reload 即新 generation。
            Files.deleteIfExists(dir.resolve("loop.js"));
            Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v2');\n");
            manager.reloadScripts();
            assertEquals(base + 1, manager.generationId());
            assertEquals("v2", recorder.value());
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void activeWatchdogEntersIsolatedFailureAndRecoversByExplicitReload() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true,
                30, 0L, 2);
        ScriptManager manager = null;
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            assertTimeoutPreemptively(Duration.ofSeconds(60), manager::loadScripts,
                    "watchdog (2s) must abort the runaway active entry within the budget");
            long failedGeneration = manager.generationId();

            // 隔离失败可观察：标记置位，不自动创建第二个 active。
            assertTrue(manager.isActiveFailed(), "killed active must enter isolated failure");
            manager.flushReadyNodeTimers();
            assertEquals(failedGeneration, manager.generationId(),
                    "timer flush must not rebuild: no second active without explicit reload");
            assertTrue(manager.isActiveFailed(), "timer flush must not clear the failure");

            // 显式 reload 是唯一恢复入口。
            Files.deleteIfExists(dir.resolve("loop.js"));
            Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('recovered');\n");
            manager.reloadScripts();
            assertFalse(manager.isActiveFailed(), "explicit reload clears the isolated failure");
            assertEquals(failedGeneration + 1, manager.generationId());
            assertEquals("recovered", recorder.value());
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    @Test
    void serverAndClientOwnersReloadIndependently() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Files.writeString(scriptsDir(ScriptType.SERVER).resolve("entry.js"),
                "TestRecorder.record('server');\n");
        Files.writeString(scriptsDir(ScriptType.CLIENT).resolve("entry.js"),
                "TestRecorder.record('client');\n");

        Recorder serverRecorder = new Recorder();
        Recorder clientRecorder = new Recorder();
        // Graal 要求共享 Engine 的 Context 使用同一 HostAccess，而每个 manager 自建
        // HostAccess：SERVER/CLIENT 各用独立 Engine（与既有测试一致）。
        Engine serverEngine = Engine.newBuilder().build();
        Engine clientEngine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptManager server = null;
        ScriptManager client = null;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            server = newManager(ScriptType.SERVER, paths, serverRecorder, serverEngine, config,
                    new DefaultErrorTracker(paths, config));
            client = newManager(ScriptType.CLIENT, paths, clientRecorder, clientEngine, config,
                    new DefaultErrorTracker(paths, config));
            server.discoverScripts();
            client.discoverScripts();
            server.loadScripts();
            client.loadScripts();
            long serverBase = server.generationId();
            long clientBase = client.generationId();
            ScriptManager finalServer = server;
            ScriptManager finalClient = client;

            Future<?> serverReload = pool.submit(() -> {
                finalServer.reloadScripts();
                return null;
            });
            Future<?> clientReload = pool.submit(() -> {
                finalClient.reloadScripts();
                return null;
            });
            serverReload.get(60, TimeUnit.SECONDS);
            clientReload.get(60, TimeUnit.SECONDS);
            assertEquals(serverBase + 1, server.generationId());
            assertEquals(clientBase + 1, client.generationId());
            assertEquals("server", serverRecorder.value());
            assertEquals("client", clientRecorder.value());
        } finally {
            pool.shutdownNow();
            forceCloseLiveContexts();
            if (server != null) {
                server.close();
            }
            if (client != null) {
                client.close();
            }
            serverEngine.close();
            clientEngine.close();
        }
    }

    @Test
    void guestThreadAdvancedCapabilitiesAreNotTightened() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path dir = scriptsDir(ScriptType.SERVER);
        Files.writeString(dir.resolve("entry.js"), "TestRecorder.record('v');\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        // allowThreads 开启：guest 线程能力保持既有语义，本票只约束 managed lifecycle 入口。
        SandboxConfig config = new SandboxConfig(true, false, false, false, true, true, false, true,
                30, 0L, 0);
        ScriptManager manager = null;
        try {
            manager = newManager(ScriptType.SERVER, paths, recorder, engine, config,
                    new DefaultErrorTracker(paths, config));
            manager.discoverScripts();
            manager.loadScripts();
            ScriptManager finalManager = manager;

            // guest 线程仍可创建并执行任意 Java 工作（能力未收紧）。
            AtomicInteger guestWork = new AtomicInteger();
            Thread guest = new Thread(() -> guestWork.set(42), "ticket07-guest-created");
            guest.setDaemon(true);
            guest.start();
            guest.join(10_000);
            assertEquals(42, guestWork.get(), "guest-created threads keep their advanced Java ability");

            // 同一 guest 线程经显式入口访问 managed lifecycle。
            Thread schedulingGuest = new Thread(() -> {
                try {
                    finalManager.scheduleOnOwner(() -> {
                        assertInstanceOf(ScriptLifecycleGate.Decision.class,
                                finalManager.requestReload());
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "ticket07-guest-scheduler");
            schedulingGuest.setDaemon(true);
            schedulingGuest.start();
            schedulingGuest.join(60_000);
            assertFalse(schedulingGuest.isAlive(), "guest scheduling must complete");
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }
}
