package com.tkisor.nekojs.core.modification;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 39 领域计划机制 fixture（common 层，零 MC）：DOMAIN_PLAN 收集阶段、候选期 inert、
 * 与 global/shared 写集的联合预检/联合成败（AC2/AC3/AC4/AC9 的机制面）。
 *
 * <p>用真实 {@link NekoRuntimeRoot} + 真实 Graal 管线 + <b>合成</b>领域收集器/Adapter
 * （synthetic:target 的数值存储，实现与真实 Item/Block Adapter 相同的
 * 「恢复基线 → 按声明序应用」契约）。真实 MC Adapter 的行为面归版本树 fixture
 * （26.x E2E 与 1.21.1 成对文件）；本文件固定的是 root 拥有的收集挂载点与联合边界语义。
 *
 * <p>断言全部走公开 seam：reload 成败结果、合成 Adapter 的公开计数/存储、
 * root.globalState() 的已提交读回；不读私有字段。
 */
class Ticket39DomainCollectionTest {

    /** 合成事件总线：脚本侧 ModEvents.modification(event => ...)。 */
    static final EventGroup GROUP = EventGroup.of("ModEvents");
    static final EventBusJS<CollectingEvent, Void> MODIFICATION =
            GROUP.server("modification", CollectingEvent.class);

    /** key-dispatch 总线（票 39 F8：收集派发必须显式拒绝带 dispatch key 的总线）。 */
    static final EventBusJS<CollectingEvent, String> DISPATCHED =
            GROUP.server("dispatched", CollectingEvent.class,
                    com.tkisor.nekojs.api.event.DispatchKey.of(String.class, event -> "synthetic:alpha"));

    /** 收集事件载荷：modify(target, cb) 产出一条规范化声明进计划。 */
    public static final class CollectingEvent {
        private final ModificationCandidatePlan plan;

        CollectingEvent(ModificationCandidatePlan plan) {
            this.plan = plan;
        }

        public void modify(String target, Consumer<PropView> modifier) {
            if (target == null || !target.contains(":")) {
                plan.fail("invalid target '" + target + "' (expected namespace:path)", null);
                return;
            }
            PropView view = new PropView();
            try {
                modifier.accept(view);
            } catch (Throwable t) {
                plan.fail("modifier callback threw for " + target, t);
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            if (view.power != null) properties.put("power", view.power);
            if (view.label != null) properties.put("label", view.label);
            plan.add(new ModificationDeclaration("synthetic", target, properties, null));
        }

        public int getDeclaredCount() {
            return plan.declarations().size();
        }
    }

    /** 合成属性视图（显式 setter；parity 面归版本树 fixture）。 */
    public static final class PropView {
        Integer power;
        String label;

        public void setPower(int power) {
            this.power = power;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    /**
     * 合成 Adapter：模拟平台目标存储与「恢复基线 → 按声明序应用」契约；
     * preflight 可注入失败，apply 计数与存储是公开观察面。
     */
    static final class SyntheticApplier implements ModificationApplier {
        final Map<String, Integer> targets = new LinkedHashMap<>();
        final Map<String, Integer> baselines = new LinkedHashMap<>();
        int preflightCount;
        int applyCount;
        String failPreflightWith;

        void seed(String target, int value) {
            targets.put(target, value);
            baselines.put(target, value);
        }

        @Override
        public String adapterId() {
            return "synthetic-test";
        }

        @Override
        public void preflight(List<ModificationDeclaration> declarations) {
            preflightCount++;
            if (failPreflightWith != null) {
                throw new IllegalStateException(failPreflightWith);
            }
            for (ModificationDeclaration declaration : declarations) {
                if (!targets.containsKey(declaration.targetId())) {
                    throw new IllegalArgumentException("unknown target: " + declaration.targetId());
                }
                Object power = declaration.properties().get("power");
                if (power instanceof Number number && number.intValue() > 100) {
                    throw new IllegalArgumentException("power out of range on " + declaration.targetId());
                }
            }
        }

        @Override
        public void apply(List<ModificationDeclaration> declarations) {
            applyCount++;
            // AC7 契约：先恢复全部 NekoJS 持有基线，再按声明顺序应用完整新计划
            targets.putAll(baselines);
            for (ModificationDeclaration declaration : declarations) {
                if ("power".equals(firstKey(declaration))) {
                    targets.put(declaration.targetId(), ((Number) declaration.properties().get("power")).intValue());
                }
            }
        }

        private static String firstKey(ModificationDeclaration declaration) {
            return declaration.properties().containsKey("power") ? "power" : null;
        }
    }

    /** 领域收集器：把收集事件派发进候选并注册计划（与真实 Item/Block 收集器同构）。 */
    static final class SyntheticCollector implements CandidateDomainCollector {
        final SyntheticApplier applier;

        SyntheticCollector(SyntheticApplier applier) {
            this.applier = applier;
        }

        @Override
        public String domain() {
            return "synthetic-modification";
        }

        @Override
        public ScriptType scriptType() {
            return ScriptType.SERVER;
        }

        @Override
        public void collect(Handle handle) {
            ModificationCandidatePlan plan = new ModificationCandidatePlan(applier);
            handle.dispatch(MODIFICATION, new CollectingEvent(plan));
            handle.registerPlan(plan);
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of();
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return Map.of("ModEvents", GROUP);
        }

        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
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

    /** 真实 root 装配（SERVER 单类型 + 合成收集器注册）。 */
    static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final SyntheticApplier applier = new SyntheticApplier();
        final SyntheticCollector collector = new SyntheticCollector(applier);
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime();
        final DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(null);
        final NekoRuntimeRoot root;

        Harness() {
            bridge.setPluginRuntime(pluginRuntime);
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = testConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, compilers, pluginRuntime, cache);
            root = new NekoRuntimeRoot(core, pluginRuntime, bridge,
                    newPropertyRegistry(), sandboxFactory, cache);
            root.registerDomainCollector(collector);
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
        }

        void writeServerScript(String name, String source) throws Exception {
            Files.writeString(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name), source);
        }

        @Override
        public void close() throws Exception {
            root.closeSilently();
            engine.close();
        }
    }

    private static SandboxConfig testConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    @AfterEach
    void cleanScriptDirs() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // ==================== AC2/AC3：候选期 inert，commit 点应用一次 ====================

    @Test
    void candidateCollectsInertPlanAndAppliesExactlyOnceAtCommit() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.applier.seed("synthetic:beta", 2);
            harness.writeServerScript("mod.js", """
                    global.duringCollection = 'unset'
                    ModEvents.modification(event => {
                      global.duringCollection = 'collected'
                      event.modify('synthetic:alpha', p => p.setPower(7))
                      event.modify('synthetic:beta', p => p.setPower(9))
                    })
                    """);
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            // 初始 load 是非事务路径：没有候选 → DOMAIN_PLAN 不跑（收集器只参与事务 reload）。
            assertEquals(0, harness.applier.applyCount, "initial non-transactional load must not run domain collection");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));

            harness.root.reload(ScriptType.SERVER);

            assertEquals(1, harness.applier.applyCount, "commit applies the plan exactly once");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));
            assertEquals(9, harness.applier.targets.get("synthetic:beta"));
            assertEquals("collected", harness.root.globalState().storeFor(ScriptType.SERVER).get("duringCollection"),
                    "collection dispatch runs inside the candidate context (global view write set published at commit)");
        }
    }

    @Test
    void candidatePlanIsInertUntilCommit() throws Exception {
        // 第一次 reload 已应用 7；第二次候选构建期间（收集派发后、commit 前）目标存储
        // 必须保持旧 active 值——注册一个 probe 收集器在 DOMAIN_PLAN 阶段记录观察值。
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            List<Integer> seenDuringCandidate = new ArrayList<>();
            CandidateDomainCollector probe = new CandidateDomainCollector() {
                @Override public String domain() { return "probe"; }
                @Override public ScriptType scriptType() { return ScriptType.SERVER; }
                @Override public void collect(Handle handle) {
                    // synthetic 收集器在本 probe 之前注册、已经 dispatch 过本轮声明——
                    // 此刻存储仍必须是旧 active 值（收集派发对 live 目标零副作用）
                    seenDuringCandidate.add(harness.applier.targets.get("synthetic:alpha"));
                }
            };
            harness.root.registerDomainCollector(probe);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(8)))");
            harness.root.reload(ScriptType.SERVER);

            assertEquals(List.of(7), seenDuringCandidate,
                    "during candidate construction the live store still holds the OLD active values");
            assertEquals(8, harness.applier.targets.get("synthetic:alpha"), "commit applies the new plan");
        }
    }

    // ==================== AC4：收集失败/预检失败 → 整批失败保留旧 active ====================

    @Test
    void collectionListenerErrorFailsTheCandidateAndKeepsOldActivePlan() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(1, harness.applier.applyCount);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // 新一轮：收集回调抛出 → DOMAIN_PLAN 失败
            harness.writeServerScript("mod.js", "ModEvents.modification(e => { throw new Error('boom in collector') })");
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().startsWith("domain-collect:synthetic-modification"),
                    "domain=" + failure.report().domain());
            assertEquals(1, harness.applier.applyCount, "failed candidate must not apply anything");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"),
                    "old active modifications keep serving (no restore, no partial apply)");
        }
    }

    @Test
    void planMarkedFailedDuringCollectionFailsJointPreflightWithoutPartialApply() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            // 未知目标（合法 id 形态）：收集期记录声明，Adapter preflight 拒绝 →
            // STATE_PLAN 联合预检失败（domain 归因到领域计划），无部分应用
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:missing', p => p.setPower(5)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                    "unknown target is rejected at the joint preflight (STATE_PLAN)");
            assertTrue(failure.report().domain().contains("state-plan-preflight"),
                    "domain=" + failure.report().domain());

            // 值域拒绝（power > 100）同样走联合预检，而不是收集期或应用期
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(500)))");
            NekoReloadException second = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, second.report().phase(),
                    "range validation joins the preflight, not the apply path");

            // 收集期 fail 标记（非法 target 文本：无命名空间）：post 不中断（旧可观察顺序），
            // 但计划在联合预检必然失败——静默 stale 不算成功
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('no-namespace', p => p.setPower(5)))");
            NekoReloadException third = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, third.report().phase());
            assertTrue(third.report().domain().contains("state-plan-preflight:" + ModificationCandidatePlan.DOMAIN),
                    "collection failure marker surfaces at the joint preflight (domain="
                            + third.report().domain() + ")");

            assertEquals(0, harness.applier.applyCount, "blocked batches must never apply");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));
        }
    }

    // ==================== AC7（机制面）：声明移除 = 恢复基线；空计划也参与 commit ====================

    @Test
    void removedDeclarationsRestoreBaselinesViaAnEmptyPlanAtCommit() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // 声明消失：空计划在 commit 应用（恢复基线），不是静默 stale
            harness.writeServerScript("mod.js", "global.noModification = true");
            harness.root.reload(ScriptType.SERVER);

            assertEquals(2, harness.applier.applyCount, "empty plan still applies at commit (restore path)");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"),
                    "removed declaration restores the NekoJS-held baseline (no silent stale)");
        }
    }

    // ==================== AC9：与 global 写集联合预检、联合成败 ====================

    @Test
    void domainPlanAndGlobalWriteSetCommitJointlyOrNotAtAll() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            // 同一脚本：global 顶层写 + 修改声明 —— 成功方向：两边一起发布
            harness.writeServerScript("mod.js", """
                    global.answer = 42
                    ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))
                    """);
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(42, harness.root.globalState().storeFor(ScriptType.SERVER).get("answer"));
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // 失败方向：Adapter preflight 拒绝 → global 写集也不发布，旧 active 保留
            harness.applier.failPreflightWith = "adapter cannot prove recoverability";
            harness.writeServerScript("mod.js", """
                    global.answer = 99
                    ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(8)))
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().contains(ModificationCandidatePlan.DOMAIN),
                    "domain=" + failure.report().domain());
            assertEquals(42, harness.root.globalState().storeFor(ScriptType.SERVER).get("answer"),
                    "global write set must not publish when the domain plan preflight fails");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"),
                    "old active plan keeps serving when the batch is blocked");
        }
    }

    @Test
    void dispatchKeyedBusIsRejectedByDomainCollectionInsteadOfSilentlyBroadcasting() throws Exception {
        // F8：按 key 定向分发的总线需要 key 才能判定投递子集，收集派发没有 key 上下文 →
        // 显式拒绝（UnsupportedOperationException）而不是「忽略 key 全量派发」静默降级。
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.root.registerDomainCollector(new CandidateDomainCollector() {
                @Override public String domain() { return "dispatch-probe"; }
                @Override public ScriptType scriptType() { return ScriptType.SERVER; }
                @Override public void collect(Handle handle) {
                    handle.dispatch(DISPATCHED, new CollectingEvent(new ModificationCandidatePlan(harness.applier)));
                }
            });
            harness.writeServerScript("mod.js", "global.noModification = true");

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().startsWith("domain-collect:dispatch-probe"),
                    "domain=" + failure.report().domain());
            assertEquals(0, harness.applier.applyCount, "rejected collection must not apply anything");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));
        }
    }

    @Test
    void rootCloseClosesCollectorsSoIndependentRootsDoNotBleed() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // AutoCloseable 收集器在 root close 时被关闭（真实域的基线恢复），注册表清空
            List<String> closed = new ArrayList<>();
            harness.root.registerDomainCollector(new AutoCloseableCandidateDomainCollector(closed));
            harness.root.closeSilently();
            assertEquals(List.of("closed"), closed, "root close must close AutoCloseable collectors (AC5)");
            assertNull(harness.root.domainCollector("close-probe"), "closed root clears its collector registry");
        }
    }

    /** AutoCloseable 收集器最小实现（close 观察面）。 */
    static final class AutoCloseableCandidateDomainCollector implements CandidateDomainCollector, AutoCloseable {
        private final List<String> closed;

        AutoCloseableCandidateDomainCollector(List<String> closed) {
            this.closed = closed;
        }

        @Override public String domain() { return "close-probe"; }
        @Override public ScriptType scriptType() { return ScriptType.SERVER; }
        @Override public void collect(Handle handle) { }
        @Override public void close() { closed.add("closed"); }
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
