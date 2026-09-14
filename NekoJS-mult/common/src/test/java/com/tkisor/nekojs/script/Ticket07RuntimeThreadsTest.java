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
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 fixture：同类型串行（AC1）、回调内拒绝（AC2）、close 优先与抢占（AC3）、
 * 非 owner 显式调度入口（AC4/AC7 guest 面）、候选 watchdog 保留 active（AC5）、
 * active watchdog 隔离失败与显式恢复（AC6）、SERVER/CLIENT owner 独立（AC7）。
 *
 * <p>断言全部走公开 seam：generation、requestReload 判决、结构化失败报告
 * （phase/domain）、isActiveFailed/isClosed/isCloseRequested、回调执行次数与
 * recorder 观察，不把私有 Map/锁字段当契约（反射强关仅用于失败兜底清理）。
 */
class Ticket07RuntimeThreadsTest {

    /** 监听器事件 payload。 */
    public static final class TestEvent {}

    /**
     * 脚本可见的测试记录器：入口计数、监听器/timer 命中、close 抢占的自旋条件
     * （宿主调用即 Graal 安全点，close 的 interrupt 在此打断候选求值）。
     * 对 manager 的 lifecycle 调用全部经 Recorder 的 Java 方法中转——ScriptManager
     * 本身不作为 guest 可见绑定暴露（与生产面一致）。
     */
    public static final class Recorder {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger listenerHits = new AtomicInteger();
        final AtomicInteger timerHits = new AtomicInteger();
        volatile String lastValue;
        /** 由 harness 回填：回调内请求/查询 close 状态用。 */
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

        /** 宿主空操作：guest 循环内的安全点锚（interrupt 的打断点）。 */
        public void spin() {
        }

        /** 自旋条件：close 已被请求（close 发起前置 false，脚本自然退出循环）。 */
        public boolean closeRequested() {
            ScriptManager current = manager;
            return current != null && current.isCloseRequested();
        }

        /** 回调内请求 reload（AC2 观察点）：返回判决文本并记录。 */
        public void requestReloadFromCallback() {
            ScriptManager current = manager;
            String decision = String.valueOf(current == null ? null : current.requestReload());
            record("reload-decision:" + decision);
        }

        /** 回调内抛出式 reload（AC2 观察点）：拒绝以结构化失败呈现，记录 domain。 */
        public void reloadThrowingFromCallback() {
            try {
                manager.reloadScripts();
                record("threw:nothing");
            } catch (NekoReloadException e) {
                record("threw:" + e.report().domain());
            }
        }
    }

    /** 提供真实事件总线（Ticket07Events.ping）的 bridge：AC2/AC5/AC6 用真实分发观察。 */
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

    /** 测试 harness：真实 bridge（Ticket07Events）+ Recorder 绑定 + 独立 Engine 的 manager。 */
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
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory);
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory);
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

    /** 健康默认配置（watchdog 关闭）：并发/串行/回调/close 抢占用例用。 */
    private static SandboxConfig quietConfig() {
        return SandboxConfig.defaultConfig();
    }

    /** 时间窗口 watchdog 配置：语句上限关闭，只有 2s 墙钟守卫可终止空循环。 */
    private static SandboxConfig wallClockWatchdogConfig(int timeoutSeconds) {
        return new SandboxConfig(false, false, false, false, true, true, false, true,
                60, 0L, timeoutSeconds);
    }

    // ---- AC1：并发 reload 单一序列、每轮恰好一个 candidate ----

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

    // ---- AC2：回调内 reload 明确拒绝，不递归、不同步等待自身 ----

    @Test
    void reloadInsideManagedCallbackIsRejectedNotRecursive() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            // 监听器体内直接请求 reload：EventBusJS 分发点已做回调深度标记，
            // 同线程 lifecycle 请求必须得到明确拒绝而不是递归开启第二个 candidate。
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

            // 抛出式入口同样以结构化失败呈现拒绝（可观察 domain），而不是死锁或递归
            h.writeScript("entry2.js", """
                    Ticket07Events.ping(() => {
                        TestRecorder.hit();
                        TestRecorder.reloadThrowingFromCallback();
                    });
                    """);
            // entry2 在候选执行时才注册新监听器；先 reload 使其生效（监听器提交后激活）
            h.manager.reloadScripts();
            h.bridge.postPing();
            assertTrue(h.recorder.value() != null && h.recorder.value().startsWith("threw:"),
                    "throwing entry must surface the rejection, got: " + h.recorder.value());
            assertTrue(h.recorder.value().contains("REJECTED_REENTRANT"),
                    "rejection domain must be observable: " + h.recorder.value());
            assertEquals(base + 1, h.manager.generationId(), "only the outer explicit reload commits");

            // 回调外同一线程恢复正常（排队/直接执行均可）
            assertEquals(ScriptLifecycleGate.Decision.EXECUTED, h.manager.requestReload());
            assertEquals(base + 2, h.manager.generationId());
        }
    }

    // ---- AC4/AC7：非 owner（guest-created）线程走显式调度入口 ----

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
                // guest 线程经显式调度入口进入：与 owner 的 lifecycle 单一序列排队执行
                Future<Long> scheduled = pool.submit(() -> h.manager.scheduleOnOwner(() -> {
                    h.manager.reloadScripts();
                    return h.manager.generationId();
                }));
                assertEquals(base + 1, scheduled.get(60, TimeUnit.SECONDS).longValue(),
                        "scheduled reload must serialize onto the owner queue and commit");

                // 非 owner 线程直接调用公开 lifecycle 同样在实例锁 monitor 队列排队
                // （不旁路、不并发触碰 Context）：排队成功的外部可观察结果。
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

    /** AC4 guest 面：高级 Java/线程能力不被收紧，managed lifecycle 只走显式入口。 */
    @Test
    void guestThreadAdvancedCapabilitiesAreNotTightened() throws Exception {
        // allowThreads 开启：guest 线程能力保持既有语义，本票只约束 managed lifecycle 入口
        SandboxConfig config = new SandboxConfig(true, false, false, false, true, true, false, true,
                60, 0L, 0);
        try (Harness h = new Harness(ScriptType.SERVER, config)) {
            h.writeScript("entry.js", "TestRecorder.record('v');\n");
            h.loadAndRun();

            AtomicInteger guestWork = new AtomicInteger();
            Thread guest = new Thread(() -> guestWork.set(42), "ticket07-guest-created");
            guest.setDaemon(true);
            guest.start();
            guest.join(10_000);
            assertEquals(42, guestWork.get(), "guest-created threads keep their advanced Java ability");

            // 同一线程经显式入口访问 managed lifecycle（guest 不触碰 Context 旁路）
            Thread schedulingGuest = new Thread(() -> {
                try {
                    Object decision = h.manager.scheduleOnOwner(() -> h.manager.requestReload());
                    assertNotNull(decision, "scheduled requestReload must return a decision");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "ticket07-guest-scheduler");
            schedulingGuest.setDaemon(true);
            schedulingGuest.start();
            schedulingGuest.join(60_000);
            assertFalse(schedulingGuest.isAlive(), "guest scheduling must complete without deadlock");
        }
    }

    // ---- AC3：close 抢占在途 candidate（中断路径）+ 幂等 + 关闭后拒绝 ----

    @Test
    void closePreemptsInFlightCandidateViaInterrupt() throws Exception {
        // watchdog 关闭：候选死循环只能被 close 的 interrupt 打断（确定性取消点）
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            assertEquals("v1", h.recorder.value());
            long base = h.manager.generationId();

            // reload 候选含空循环脚本：close 的 interrupt 在安全点打断候选求值
            h.writeScript("loop.js", "while (true) { TestRecorder.spin(); }\n");
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
                // close 优先：请求标志 → 中断在途候选 → 实例锁上等 owner 退出 → teardown
                assertTimeoutPreemptively(Duration.ofSeconds(30), h.manager::close,
                        "close must preempt the in-flight candidate instead of hanging");
                assertTrue(h.manager.isClosed(), "close must complete after preemption");
                NekoReloadException failure = reload.get(30, TimeUnit.SECONDS);
                assertNotNull(failure, "in-flight reload must fail, never commit after close");
                assertEquals("close-preempted", failure.report().domain(),
                        "preemption must be observable: " + failure.report().describe());
                assertEquals(base, h.manager.generationId(), "preempted candidate must not commit");
                assertEquals("v1", h.recorder.value(), "active scripts must be untouched by preemption");

                // 幂等：重复 close 不抛；已关闭后新 lifecycle 明确拒绝
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

    // ---- AC3：close 抢占在途 candidate（取消点收敛 + 未开始脚本不再启动 + 永不 commit）----

    /**
     * 候选脚本自旋到 close 请求后自然退出：抢占经由取消点呈现——可能是中断
     * （phase=EXECUTION/candidate 求值被打断）、脚本间检查（phase=EXECUTION/后续脚本
     * 不再启动）或 commit 前检查（phase=COMMIT/候选执行完但被丢弃），三者不可在线程
     * 竞态下区分，但外部可观察结果收敛：domain=close-preempted、generation 不变、
     * 未开始的候选脚本（second.js）绝不执行、候选永不 commit。
     */
    @Test
    void closePreemptsRemainingCandidateScriptsAndNeverCommits() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            long base = h.manager.generationId();

            // gate 自旋到 close 请求即退出；second 排在其后（字母序），一旦取消点生效
            // 就不应再启动
            h.writeScript("gate.js", "while (!TestRecorder.closeRequested()) { TestRecorder.spin(); }\nTestRecorder.record('gate-passed');\n");
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

    /** AC3「close 优先于尚未开始的 reload」：close 请求后新 lifecycle 被明确拒绝。 */
    @Test
    void closeIsPrioritizedOverNotStartedReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, quietConfig())) {
            h.writeScript("entry.js", "TestRecorder.record('v1');\n");
            h.loadAndRun();
            h.manager.close();
            assertTrue(h.manager.isCloseRequested());
            assertTrue(h.manager.isClosed());
            // 尚未开始的 reload/load 在门处拒绝（排队也不会执行）
            NekoReloadException reloadRejected = assertThrows(NekoReloadException.class, h.manager::reloadScripts);
            assertTrue(reloadRejected.report().domain().contains("REJECTED_CLOSED"));
            NekoReloadException loadRejected = assertThrows(NekoReloadException.class, h.manager::loadScripts);
            assertTrue(loadRejected.report().domain().contains("REJECTED_CLOSED"));
        }
    }

    // ---- AC5：watchdog 终止 candidate，active 事件/timer/state 不变；显式 reload 可再来 ----

    @Test
    void candidateWatchdogRetainsActiveAndRecoversByExplicitReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, wallClockWatchdogConfig(2))) {
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    setTimeout(() => TestRecorder.timerHit(), 0);
                    TestRecorder.record('v1');
                    """);
            h.loadAndRun();
            h.bridge.postPing();
            // setTimeout(0) 的 ready 入队由调度线程异步完成：轮询 flush 直到命中
            // （上限 1s，避免调度唤醒延迟造成的不确定）
            for (int i = 0; i < 200 && h.recorder.timerHits() == 0; i++) {
                h.manager.flushReadyNodeTimers();
                Thread.sleep(5);
            }
            int hitsBefore = h.recorder.hits();
            int timerHitsBefore = h.recorder.timerHits();
            assertEquals(1, hitsBefore);
            assertEquals(1, timerHitsBefore);
            long base = h.manager.generationId();

            // 候选含空循环：语句上限关闭（0），只有墙钟守卫（2s）能终止它
            h.writeScript("loop.js", "while (true) { /* spin forever */ }\n");
            NekoReloadException failure = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                try {
                    h.manager.reloadScripts();
                    return null;
                } catch (NekoReloadException e) {
                    return e;
                }
            }, "wall-clock watchdog (2s) must abort the runaway candidate");
            assertNotNull(failure, "runaway candidate must fail, never commit");
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase(),
                    "watchdog kill surfaces at execution: " + failure.report().describe());
            assertEquals(base, h.manager.generationId(), "failed candidate must retain the active generation");
            assertFalse(h.manager.isActiveFailed(), "candidate kill must not poison the active");

            // active 的事件、timer、state 全部不变：监听器继续命中、旧 state 仍可读
            h.bridge.postPing();
            assertEquals(hitsBefore + 1, h.recorder.hits(), "active listener must keep receiving events");
            h.manager.flushReadyNodeTimers();
            assertEquals(timerHitsBefore, h.recorder.timerHits(), "no new active timers may appear from the candidate");
            assertEquals("v1", h.recorder.value(), "active script state must be untouched");

            // 显式 reload 是恢复入口：修好脚本后一次成功 reload 即新 candidate 提交
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

    // ---- AC6：watchdog 终止 active → 隔离失败、停止分发、显式 reload 唯一恢复 ----

    @Test
    void activeWatchdogEntersIsolatedFailureAndRecoversByExplicitReload() throws Exception {
        try (Harness h = new Harness(ScriptType.SERVER, wallClockWatchdogConfig(2))) {
            // entry 先注册监听器；loop 按字母序在后，watchdog 终止的是 active 求值
            h.writeScript("entry.js", """
                    Ticket07Events.ping(() => TestRecorder.hit());
                    TestRecorder.record('v1');
                    """);
            h.writeScript("loop.js", "while (true) { /* spin forever */ }\n");
            h.manager.discoverScripts();
            assertTimeoutPreemptively(Duration.ofSeconds(60), h.manager::loadScripts,
                    "wall-clock watchdog (2s) must abort the runaway active entry");
            long failedGeneration = h.manager.generationId();

            // 隔离失败可观察：标记置位；timer flush / 事件分发都不重建、不清除
            assertTrue(h.manager.isActiveFailed(), "killed active must enter isolated failure");
            h.manager.flushReadyNodeTimers();
            assertEquals(failedGeneration, h.manager.generationId(),
                    "timer flush must not rebuild: no second active without explicit reload");
            assertTrue(h.manager.isActiveFailed(), "timer flush must not clear the isolated failure");

            // 停止向被杀 Context 分发：监听器闭包指向死环境，post 一次不再执行
            h.bridge.postPing();
            assertEquals(0, h.recorder.hits(), "dispatch must stop reaching the killed active context");
            assertEquals(failedGeneration, h.manager.generationId(),
                    "event dispatch must not rebuild: no second active");

            // 显式 reload 是唯一恢复入口：修好脚本后 reload 提交新 generation 并恢复分发
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

    // ---- AC7：SERVER 与 CLIENT owner 独立（各自 owner 线程并发 reload 不互相干扰）----

    @Test
    void serverAndClientOwnersReloadIndependently() throws Exception {
        // SERVER/CLIENT 各用独立 Engine（Graal 要求共享 Engine 的 Context 使用同一 HostAccess）
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

    // ---- AC9：SERVER/CLIENT/STARTUP/TEST 四类 owner 入口复用既有 root 生命周期入口 ----

    @Test
    void fourOwnerEntriesReuseRootLifecycleGate() throws Exception {
        // 票 05 的 NekoRuntimeRoot 是唯一 runtime owner：四类 owner 的 load/reload/test
        // 全部经 root 生命周期入口进入票 07 的同一调度门；root close 后四类 manager 的
        // lifecycle 全部进入拒绝（没有第二 manager 或旁路面）。
        Engine engine = Engine.newBuilder().build();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = quietConfig();
        Ticket07EventBridge bridge = new Ticket07EventBridge();
        Recorder recorder = new Recorder();
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, bridge.group);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
        NekoRuntimeRoot root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory);
        try {
            Files.createDirectories(ScriptTypeEnv.scriptsDir(ScriptType.STARTUP));
            for (ScriptType type : ScriptType.values()) {
                root.createScriptManager(type).discoverScripts();
            }
            ScriptManager serverManager = root.scriptManagerOf(ScriptType.SERVER);
            ScriptManager testManager = root.scriptManagerOf(ScriptType.TEST);

            // 四类 owner 入口全部经 root 生命周期入口执行（空脚本目录 = 空转成功）
            assertTrue(root.reload(ScriptType.SERVER).success(), "SERVER reload through root entry");
            assertTrue(root.reload(ScriptType.CLIENT).success(), "CLIENT reload through root entry");
            NekoRuntimeRoot.ReloadResult startup = root.reload(ScriptType.STARTUP);
            assertTrue(startup.success() && startup.nonTransactional(),
                    "STARTUP reload through root entry keeps its explicit non-transactional boundary");
            assertNotNull(root.runTests(), "TEST run through root entry");

            // root close 后：同一调度门上的 lifecycle 全部拒绝（不出现第二恢复面）
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
