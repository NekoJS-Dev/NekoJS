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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ticket 06 characterization + generation 契约测试：钉住 /nekojs reload 的候选环境行为，
 * 并在 06 实施后作为行为保持的锚。
 *
 * <p>Phase 1（characterization）先以<strong>当前实现</strong>的行为写断言；Phase 2/3 改造为
 * candidate generation 隔离 + 单一 commit 点后，把其中因 AC 改进而失效的断言更新为新契约
 * （每处更新在注释中标注 ticket 06）。以下行为<strong>不</strong>随后续 phase 改变：
 * 成功 reload 的脚本重跑、失败保留旧 Context、commit 后无双执行、timer 生产分发单一归属。
 */
class ScriptReloadGenerationTest {

    /** 监听器事件 payload。 */
    public static final class TestEvent {}

    /** 注入脚本的计数器绑定：按 tag 分别计数，用于区分旧/新 generation 的回调来源。 */
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

    /** 注入脚本的 Java 侧事件触发器：脚本执行中途同步 post 生产事件（AC1 观察点）。 */
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

    /** 提供一个真实 {@link EventGroup}（TestEvents.ping 总线）的 bridge，脚本可注册监听器。 */
    static final class TestEventBridge implements ScriptEventBridge {
        final EventGroup group = EventGroup.of("TestEvents");
        final EventBusJS<TestEvent, Void> pingBus;
        final List<ScriptType> bindEventsCalls = new CopyOnWriteArrayList<>();
        final List<ScriptType> clearListenersCalls = new CopyOnWriteArrayList<>();

        TestEventBridge() {
            this.pingBus = group.server("ping", TestEvent.class);
        }

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindEventsCalls.add(type);
            bindings.putMember("TestEvents", new EventGroupJS(group, type));
        }

        @Override
        public void clearListeners(ScriptType type) {
            clearListenersCalls.add(type);
            group.clearListeners(type);
        }

        /** 触发一次生产事件分发，返回各监听器执行后 Counter 的观察由测试负责。 */
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

        StubPluginRuntime(Counter counter, EventGroup sharedGroup, Trigger trigger) {
            this.counter = counter;
            this.sharedGroup = sharedGroup;
            this.trigger = trigger;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Counter", Binding.of("Counter", counter),
                    "Trigger", Binding.of("Trigger", trigger));
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            // 与 bridge 持有同一组实例：schema 收割与 bindEvents 指向同一条总线
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

    /** 测试 manager 装配：真实 bridge（可注册监听器）+ Counter/Trigger 绑定 + 真实 Graal 环境。 */
    private static final class ManagerHarness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final TestEventBridge bridge = new TestEventBridge();
        final Counter counter = new Counter();
        final Trigger trigger = new Trigger();
        final StubPluginRuntime pluginRuntime;
        final ScriptManager manager;
        final ScriptType scriptType;

        ManagerHarness(SandboxConfig config) {
            this(config, ScriptType.SERVER);
        }

        ManagerHarness(SandboxConfig config, ScriptType scriptType) {
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            this.pluginRuntime = new StubPluginRuntime(counter, bridge.group, trigger);
            trigger.bind(bridge::postTestEvent);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory);
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory);
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

        @Override
        public void close() {
            manager.close();
            engine.close();
        }
    }

    private static SandboxConfig withStatementLimit() {
        // 与 ScriptReloadRegressionTest 相同的测试沙盒：5s 求值超时 + 100k 语句上限
        return new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
    }

    /**
     * 成功 reload：脚本在新 generation 重跑一次，marker 记录 v1 → v2；
     * commit 后同一事件只由新 generation 监听器执行一次（无双执行）。
     */
    @Test
    void successfulReloadRunsScriptsOnceAndSingleExecutionAfterCommit() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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
     * 失败 reload（候选被语句上限杀死）：旧 Context 保留、旧脚本状态可读、
     * <strong>active 监听器继续接收事件</strong>（ticket 06 generation 隔离契约 AC2/AC3）。
     */
    @Test
    void failedReloadKeepsOldContextAndScriptState() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", """
                    Counter.hit('v1-entry');
                    TestEvents.ping(function (event) { Counter.hit('v1-listener'); });
                    """);
            harness.loadAndRun();
            assertEquals(1, harness.counter.hitsOf("v1-entry"));
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-listener"));

            Context oldContext = currentContext(harness.manager);
            harness.writeScript("entry.js", "while (true) { /* spin forever */ }\n");
            RuntimeException failure = assertThrows(RuntimeException.class, harness::reload,
                    "candidate killed by statement limit must fail the reload");

            assertEquals(oldContext, currentContext(harness.manager),
                    "failed reload must keep the old context as current");
            harness.bridge.postTestEvent();
            assertEquals(1, harness.counter.hitsOf("v1-entry"),
                    "old entry must not re-run after failed reload");
            assertEquals(2, harness.counter.hitsOf("v1-listener"),
                    "active listener must keep receiving events after candidate failure (ticket 06 AC2)");
            // AC3：失败结果外部可见地携带 generation / phase / owner / domain（无修复指引）
            assertInstanceOf(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class, failure);
            var report = ((com.tkisor.nekojs.core.lifecycle.NekoReloadException) failure).report();
            assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.EXECUTION, report.phase());
            assertTrue(report.generation() >= 1, "failure report must carry the candidate generation");
            assertEquals("ScriptManager[server]", report.owner());
            assertTrue(report.describe().contains("phase=EXECUTION"),
                    "describe() must expose the structured fields: " + report.describe());
        }
    }

    /**
     * 失败 reload 后 active 监听器仍在总线上（ticket 06：候选监听器只收集，
     * 失败随候选丢弃，active 的生产路由不动）。
     */
    @Test
    void failedReloadKeepsActiveListenersOnBus() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            assertTrue(harness.bridge.busHasListeners(), "listener registered on load");

            harness.writeScript("entry.js", "while (true) { /* spin forever */ }\n");
            assertThrows(RuntimeException.class, harness::reload);
            assertTrue(harness.bridge.busHasListeners(),
                    "candidate failure must not touch the active generation's listeners");
        }
    }

    /**
     * candidate 隔离：失败 reload 时候选注册的监听器不得留在总线上（只收集、未激活），
     * active 监听器不受影响。
     */
    @Test
    void failedReloadDiscardsCandidateListenersOnly() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();

            // 候选脚本先注册 v2 监听器（进入候选收集器），随后的死循环杀死候选
            harness.writeScript("entry.js", """
                    TestEvents.ping(function (event) { Counter.hit('v2'); });
                    while (true) { /* spin forever */ }
                    """);
            assertThrows(RuntimeException.class, harness::reload);

            harness.bridge.postTestEvent();
            assertEquals(0, harness.counter.hitsOf("v2"),
                    "candidate listener must never fire: collected pre-commit, discarded with the failed candidate");
            assertEquals(1, harness.counter.hitsOf("v1"),
                    "active listener must still receive the event exactly once (no candidate residue)");
        }
    }

    /**
     * commit 后 timer 生产分发单一归属：旧 generation 的 interval 随 commit 取消，
     * 新 generation 的 interval 继续被生产 flush 分发，同一 tick 不双执行。
     */
    @Test
    void afterCommitProductionTimerDispatchBelongsToNewGenerationOnly() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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
     * bindEvents / clearListeners 生命周期计数：initial load 只 bind 一次；
     * 失败 reload（ticket 06）不触碰总线——监听器清扫只发生在 commit 点；
     * 成功 reload 的清扫恰好一次（旧 generation 停止接收新 callback 的观测面），
     * 且清扫后只有新 generation 的监听器在总线上（无累积、无双注册）。
     */
    @Test
    void bridgeLifecycleCallCounts() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();
            assertEquals(List.of(ScriptType.SERVER), harness.bridge.bindEventsCalls,
                    "initial load binds event groups exactly once");

            harness.writeScript("entry.js", "while (true) { /* spin forever */ }\n");
            assertThrows(RuntimeException.class, harness::reload);
            assertTrue(harness.bridge.clearListenersCalls.isEmpty(),
                    "failed reload must not clear any listeners (ticket 06: sweep happens only at commit)");

            // 成功 reload：完整清扫恰好一次（在 commit 点），随后只有新 generation 的监听器
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
     * AC1：candidate 执行期间，真实生产事件仍由 active 监听器执行；
     * 候选监听器（已注册进候选收集器）不得接收 commit 前的生产事件。
     */
    @Test
    void candidatePhaseEventsServedByActiveGeneration() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "TestEvents.ping(function (event) { Counter.hit('v1'); });\n");
            harness.loadAndRun();

            // 候选脚本：先注册候选监听器（进入候选收集器），再从脚本执行中途同步 post 生产事件，
            // 最后死循环杀死候选。post 发生在 candidate phase 内（生产路由仍属 active）。
            harness.writeScript("entry.js", """
                    TestEvents.ping(function (event) { Counter.hit('v2'); });
                    Trigger.now();
                    while (true) { /* spin forever */ }
                    """);
            assertThrows(RuntimeException.class, harness::reload);

            assertTrue(harness.counter.hitsOf("v1") >= 1,
                    "production event during candidate phase must be served by the active listener (AC1)");
            assertEquals(0, harness.counter.hitsOf("v2"),
                    "candidate listener must not receive pre-commit production events (AC1)");
        }
    }

    /**
     * 连续多次 commit：每个 generation 只执行一次、旧 Context 关闭、generation 单调递增。
     */
    @Test
    void consecutiveCommitsKeepSingleExecutionAndCloseOldContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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

            // 旧 generation 的 Context 已随 commit 关闭（释放的可观察结果）
            assertThrows(Exception.class, () -> contextV1.eval("js", "1 + 1"),
                    "old generation context must be closed at commit");
            assertThrows(Exception.class, () -> contextV2.eval("js", "1 + 1"),
                    "previous generation context must be closed at the next commit");
            assertEquals("2", contextV3.eval("js", "String(1 + 1)").asString(),
                    "the committed generation context stays usable");
        }
    }

    /**
     * AC5（提交面）：候选注册的 pending timer 随 generation 提交——commit 后由生产
     * flush 分发（一次）。候选执行期间的 timer 只进候选收集器，不提前进入生产路由。
     */
    @Test
    void pendingCandidateTimerCommittedWithGeneration() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "Counter.hit('v1-entry');\n");
            harness.loadAndRun();

            // 候选脚本（.mjs：顶层 await）：注册 200ms 一次性 timer（commit 时仍未就绪 → 只被收集），
            // await 一个 0ms timer 证明候选执行路径上的 timer 回调可用（TLA 恢复）
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

    /** AC5（丢弃面）：失败候选的 pending timer 随候选关闭，永不进入生产分发。 */
    @Test
    void pendingCandidateTimerDiscardedWithFailedCandidate() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", "setInterval(function () { Counter.hit('v1-timer'); }, 40);\n");
            harness.loadAndRun();
            Thread.sleep(120);
            harness.manager.flushReadyNodeTimers();
            int v1Before = harness.counter.hitsOf("v1-timer");
            assertTrue(v1Before >= 1, "active interval must be ticking before the failed reload");

            harness.writeScript("entry.js", """
                    setTimeout(function () { Counter.hit('v2-timer'); }, 200);
                    while (true) { /* spin forever after registering the timer */ }
                    """);
            assertThrows(RuntimeException.class, harness::reload);

            Thread.sleep(300);
            harness.manager.flushReadyNodeTimers();
            assertEquals(0, harness.counter.hitsOf("v2-timer"),
                    "failed candidate's pending timer must be discarded with its generation");
            assertTrue(harness.counter.hitsOf("v1-timer") > v1Before,
                    "active generation's timer keeps being dispatched after candidate failure");
        }
    }

    /**
     * AC5（TEST 面）：TEST 的候选测试 callback 在测试 harness 中执行——candidate
     * 监听器 commit 后激活、pending timer 由 flushTestTimers 冲刷；失败重载后旧
     * TEST 环境仍可用。
     */
    @Test
    void testTypeCandidateCallbacksRunInHarness() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit(), ScriptType.TEST)) {
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

            harness.writeScript("entry.js", "while (true) { /* spin forever */ }\n");
            assertThrows(RuntimeException.class, harness::runTests,
                    "candidate killed by statement limit must fail the TEST run");
            harness.bridge.postTestEvent();
            assertEquals(2, harness.counter.hitsOf("t1-listener"),
                    "failed TEST candidate keeps the old TEST listeners alive");
            assertEquals(1, harness.counter.hitsOf("t1-timer"),
                    "old TEST timer count unchanged");
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
}
