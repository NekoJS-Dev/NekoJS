package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 10（global/shared 候选写集）逐 AC fixture：root 拥有的按 ScriptType backing store、
 * 显式 shared 入口、候选写集（read-your-writes / 失败丢弃 / 冲突检测 / 联合提交）、
 * 生命周期保留、guest Value 失效、global vs globalThis 分工、旧跨类型迁移。
 *
 * <p>断言走公开 seam：脚本 eval/reload 的可观察结果 + root 拥有的
 * {@link GlobalStateStores}（Java 侧「其他 writer」与读回观察点）+ Marker 绑定记录的
 * 脚本侧读回；不触碰私有 Map/锁字段。
 */
class Ticket10GlobalStateTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void cleanScriptDirs() throws Exception {
        for (ScriptType type : ScriptType.values()) {
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

    @AfterEach
    void cleanScriptDirsAfter() throws Exception {
        for (ScriptType type : ScriptType.values()) {
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


    // ==================== 测试绑定（公开 seam 的最小载体） ====================

    public static final class Marker {
        public final List<String> hits = new ArrayList<>();

        public void hit(String tag) {
            hits.add(tag);
        }

        public int countOf(String prefix) {
            return (int) hits.stream().filter(h -> h.startsWith(prefix)).count();
        }

        public Optional<String> first(String prefix) {
            return hits.stream().filter(h -> h.startsWith(prefix)).findFirst();
        }
    }

    /**
     * 脚本侧的已提交状态读写 + 候选计划注册 seam（「其他 writer」经 root 拥有的
     * GlobalStateStores 公开 API 直接提交，不经过任何 generation 视图）。
     */
    public static final class StateTool {
        private final GlobalStateStores stores;
        private final Marker marker;

        StateTool(GlobalStateStores stores, Marker marker) {
            this.stores = stores;
            this.marker = marker;
        }

        private GlobalStore store(String scope) {
            return switch (scope) {
                case "shared" -> stores.sharedStore();
                case "startup" -> stores.storeFor(ScriptType.STARTUP);
                case "server" -> stores.storeFor(ScriptType.SERVER);
                case "client" -> stores.storeFor(ScriptType.CLIENT);
                case "test" -> stores.storeFor(ScriptType.TEST);
                default -> throw new IllegalArgumentException("unknown scope: " + scope);
            };
        }

        /** 读<b>已提交</b>状态（绕过当前 generation 写集——观察 commit 前的真实已发布值）。 */
        public String read(String scope, String key) {
            Object value = store(scope).get(key);
            return value == null ? null : String.valueOf(value);
        }

        /** 「其他 writer」直接写已提交 store（Java 侧 seam；版本/epoch 随之 bump）。 */
        public void write(String scope, String key, String value) {
            store(scope).put(key, value);
        }

        /**
         * 候选执行期间注册联合计划（mode：fail-preflight / publish-throw / pass /
         * other-writer-preflight）。经 ScriptManager.registerCandidatePlan 公开 seam。
         */
        public boolean plan(String domain, String mode) {
            Context current = Context.getCurrent();
            return ScriptManager.registerCandidatePlan(current, new CandidateStatePlan() {
                @Override public String domain() { return domain; }
                @Override public void preflight() {
                    if ("fail-preflight".equals(mode)) {
                        throw new IllegalStateException("plan preflight boom: " + domain);
                    }
                    if ("other-writer-preflight".equals(mode)) {
                        // 模拟「STATE_PLAN 与 commit 之间闯入的其他 writer」：写集校验已经
                        // 跑完，这里的已提交写入只能由 commit 点的锁内复验捕获。
                        stores.sharedStore().put("backstopKey", "other-writer");
                    }
                }
                @Override public void publish() {
                    if ("publish-throw".equals(mode)) {
                        throw new IllegalStateException("plan publish boom: " + domain);
                    }
                    marker.hit("plan-published:" + domain);
                }
            });
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Marker marker;
        private final StateTool state;

        StubPluginRuntime(Marker marker, StateTool state) {
            this.marker = marker;
            this.state = state;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Marker", Binding.of("Marker", marker),
                    "State", Binding.of("State", state));
        }

        @Override public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() { return Map.of(); }
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

    /** 多类型 manager 装配：一个 root 状态域 + 真实 Graal 环境（无事件组，专注状态语义）。 */
    static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final GlobalStateStores stores = new GlobalStateStores();
        final Marker marker = new Marker();
        final StateTool state = new StateTool(stores, marker);
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime(marker, state);
        final Map<ScriptType, ScriptManager> managers = new EnumMap<>(ScriptType.class);

        Harness() {
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = testConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
            ScriptEnvironmentFactory environmentFactory = new ScriptEnvironmentFactory(
                    ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory, stores);
            for (ScriptType type : ScriptType.values()) {
                managers.put(type, new ScriptManager(type, ScriptEventBridge.EMPTY, pluginRuntime,
                        newPropertyRegistry(), tracker, paths, config, environmentFactory));
            }
        }

        ScriptManager managerOf(ScriptType type) {
            return managers.get(type);
        }

        void writeScript(ScriptType type, String fileName, String source) throws Exception {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), source);
        }

        void load(ScriptType type) {
            managerOf(type).discoverScripts();
            managerOf(type).loadScripts();
        }

        void reload(ScriptType type) {
            managerOf(type).reloadScripts();
        }

        void runTests() {
            managerOf(ScriptType.TEST).runTestScripts();
        }

        @Override
        public void close() {
            for (ScriptManager manager : managers.values()) {
                try {
                    manager.close();
                } catch (Throwable ignored) {
                }
            }
            engine.close();
        }
    }

    private static SandboxConfig testConfig() {
        // 5s 墙钟 + 100k 语句上限：失败注入靠语句上限烧尽（票 06 实证档位）
        return new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
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

    // ==================== AC1：同类型共享 / 四类型隔离 ====================

    @Test
    void sameTypeFilesShareGlobalAndAllFourTypesReadBackIndependentValues() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.STARTUP, "entry.js",
                    "global.same = 'startup'");
            harness.writeScript(ScriptType.SERVER, "a.js",
                    "global.count = (global.count || 0) + 1; global.same = 'server'");
            harness.writeScript(ScriptType.SERVER, "b.js",
                    "global.count = (global.count || 0) + 10; Marker.hit('b-sees-a:' + global.same)");
            harness.writeScript(ScriptType.CLIENT, "entry.js",
                    "global.same = 'client'; Marker.hit('client-sees-server:' + (typeof global.count))");
            harness.writeScript(ScriptType.TEST, "entry.js",
                    "global.same = 'test'");

            harness.load(ScriptType.STARTUP);
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);
            harness.runTests();

            // 同类型多文件共享：b.js 读到 a.js 的写入（count 累加、same 已就位）
            assertEquals("b-sees-a:server", harness.marker.first("b-sees-a").orElseThrow(),
                    "second file of the same type must see the first file's global writes");
            assertEquals("client-sees-server:undefined", harness.marker.first("client-sees-server").orElseThrow(),
                    "CLIENT must not observe SERVER's private global (per-type backing store)");

            // 四类型同名 key 读回彼此独立
            assertEquals("startup", harness.stores.storeFor(ScriptType.STARTUP).get("same"));
            assertEquals("server", harness.stores.storeFor(ScriptType.SERVER).get("same"));
            assertEquals("client", harness.stores.storeFor(ScriptType.CLIENT).get("same"));
            assertEquals("test", harness.stores.storeFor(ScriptType.TEST).get("same"));
            assertEquals(11, harness.stores.storeFor(ScriptType.SERVER).get("count"),
                    "same-type multi-file accumulation");
        }
    }

    // ==================== AC2：read-your-writes / 成功发布 / 失败丢弃 ====================

    @Test
    void candidateReadsYourWritesAndSuccessfulCommitPublishes() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js",
                    "global.keep = 'v1'; global.a = 1; global.b = 2");
            harness.load(ScriptType.SERVER);

            // 候选：read-your-writes + 读到旧 active 的已提交值（状态跨 reload 可见）
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.ryw = 'cand'
                    Marker.hit('ryw:' + global.ryw)
                    Marker.hit('sees-committed:' + global.keep)
                    Marker.hit('sees-committed-a:' + global.a)
                    """);
            // 候选执行后、commit 前不可见（reload 内联完成，这里以 commit 前的已提交读回观察）
            harness.reload(ScriptType.SERVER);

            assertEquals("ryw:cand", harness.marker.first("ryw").orElseThrow(),
                    "candidate must read back its own top-level writes (read-your-writes)");
            assertEquals("sees-committed:v1", harness.marker.first("sees-committed").orElseThrow(),
                    "candidate must see the previous generation's committed values");
            assertEquals("cand", harness.stores.storeFor(ScriptType.SERVER).get("ryw"),
                    "successful commit publishes the candidate write set");

            // 候选期的写不进已提交 store（在 reload 完成后无从观察 mid-flight，这里用
            // 下一个失败候选证明：失败时 set/delete/clear 全部不发布）
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.a = 99
                    delete global.b
                    global.clear()
                    global.c = 3
                    Marker.hit('cand-sees-clear:' + (typeof global.a) + ':' + (typeof global.keep))
                    while (true) { }
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "candidate killed by the statement limit must fail the reload");
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase(), failure.report().describe());

            // 失败丢弃：set / delete / clear 全部不污染旧 active
            assertEquals(1, harness.stores.storeFor(ScriptType.SERVER).get("a"),
                    "failed candidate's set must be discarded");
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("b"),
                    "failed candidate's delete must be discarded");
            assertEquals(4, harness.stores.storeFor(ScriptType.SERVER).size(),
                    "failed candidate's clear must be discarded; old keys intact");
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("c"),
                    "writes after clear in the failed candidate must not publish");
            assertEquals("ryw:cand", harness.marker.first("ryw").orElseThrow()); // marker untouched

            // 旧 active 仍可用：下一次 reload 恢复
            harness.writeScript(ScriptType.SERVER, "entry.js", "global.keep = 'v3'");
            harness.reload(ScriptType.SERVER);
            assertEquals("v3", harness.stores.storeFor(ScriptType.SERVER).get("keep"));
        }
    }

    // ==================== AC3：冲突检测 / 不丢写 / 跨类型私有不冲突 / shared 竞争 ====================

    @Test
    void otherWriterDuringCandidacyConflictsWithoutLosingTheOtherWriterValue() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "global.k = 'base'");
            harness.load(ScriptType.SERVER);

            // 候选首写 k（基线=当时的已提交版本），随后「其他 writer」提交同一受管 key
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.k = 'cand'
                    State.write('server', 'k', 'other')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "another writer committing the same managed key during candidacy must fail the candidate");
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                    "conflict must be reported at the joint state-plan phase: " + failure.report().describe());
            assertEquals("global-write-conflict", failure.report().domain(),
                    failure.report().describe());
            assertEquals("other", harness.stores.storeFor(ScriptType.SERVER).get("k"),
                    "the other writer's committed value must be retained (no lost write);"
                            + " the candidate's stale write must not overwrite it");
        }
    }

    @Test
    void crossTypeSamePrivateKeyDoesNotConflict() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);

            // SERVER 候选写私有 global.k 的同时，CLIENT（active）写自己的私有 global.k：
            // 不同 store 各自版本，互不冲突，两边都成立
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.k = 'server'
                    State.write('client', 'k', 'client')
                    """);
            harness.reload(ScriptType.SERVER);

            assertEquals("server", harness.stores.storeFor(ScriptType.SERVER).get("k"));
            assertEquals("client", harness.stores.storeFor(ScriptType.CLIENT).get("k"),
                    "cross-type same private key must not conflict (separate backing stores)");
        }
    }

    @Test
    void sharedCompetingWriteFailsTheCandidateAndKeepsTheCompetingValue() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    shared.handoff = 'server-cand'
                    State.write('shared', 'handoff', 'client-won')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "competing writes on the explicit shared entry must be detected");
            assertEquals("global-write-conflict", failure.report().domain(), failure.report().describe());
            assertEquals("client-won", harness.stores.sharedStore().get("handoff"),
                    "the competing committed value stays; the candidate's shared write is not published");
        }
    }

    // ==================== AC4：一次候选同时写 global 与 shared，联合成败 ====================

    @Test
    void candidateWritingBothGlobalAndSharedCommitsJointlyOrNotAtAll() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            // 失败方向：联合预检里外部计划失败 → 两边全部不发布（无半提交）
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.joint = 'g'
                    shared.joint = 's'
                    State.plan('joint-fixture', 'fail-preflight')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "a failing joined plan must fail the whole candidate");
            assertEquals("state-plan-preflight:joint-fixture", failure.report().domain(),
                    failure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("joint"),
                    "private global write set must not publish (joint failure)");
            assertNull(harness.stores.sharedStore().get("joint"),
                    "shared write set must not publish (joint failure) — no half commit");

            // 成功方向：两边联合发布
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.joint = 'g'
                    shared.joint = 's'
                    Marker.hit('candidate-sees-both:' + global.joint + ':' + shared.joint)
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("g", harness.stores.storeFor(ScriptType.SERVER).get("joint"));
            assertEquals("s", harness.stores.sharedStore().get("joint"));
            assertEquals("candidate-sees-both:g:s", harness.marker.first("candidate-sees-both").orElseThrow());
        }
    }

    // ==================== AC5：联合预检边界（无领域语义） ====================

    @Test
    void candidateStatePlanBoundaryJoinsJointPreflightAndJointOutcome() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            // 1) 通过的计划：preflight → publish 都执行，写集联合发布
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p = 'p'
                    State.plan('passing-plan', 'pass')
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("p", harness.stores.storeFor(ScriptType.SERVER).get("p"));
            assertEquals(1, harness.marker.countOf("plan-published:passing-plan"),
                    "a passing plan's publish must run exactly once at the commit point");

            // 2) 计划 preflight 在 STATE_PLAN 抛出 → candidate 失败、global/shared 不发布
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p2 = 'p2'
                    shared.p2 = 's2'
                    State.plan('test-plan', 'fail-preflight')
                    """);
            NekoReloadException preflightFailure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, preflightFailure.report().phase());
            assertEquals("state-plan-preflight:test-plan", preflightFailure.report().domain(),
                    preflightFailure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("p2"));
            assertNull(harness.stores.sharedStore().get("p2"));
            assertEquals(1, harness.marker.countOf("plan-published:passing-plan"),
                    "the failing candidate must not publish any plan");

            // 3) 计划违反 publish 契约（preflight 通过后 publish 抛出）→ global/shared 写集
            //    不发布（计划边界：其自身部分副作用不回滚，由计划作者负责）
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p3 = 'p3'
                    State.plan('publish-violator', 'publish-throw')
                    """);
            NekoReloadException publishFailure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER));
            assertEquals("state-plan-publish:publish-violator", publishFailure.report().domain(),
                    publishFailure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("p3"),
                    "global/shared write sets must stay unpublished when a plan violates publish");

            // 4) STATE_PLAN 与 commit 之间闯入的其他 writer：commit 点锁内复验兜底
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    shared.backstopKey = 'cand'
                    State.plan('sneaky-writer', 'other-writer-preflight')
                    """);
            NekoReloadException backstop = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "a writer sneaking in between STATE_PLAN and commit must be caught by the locked re-validation");
            assertEquals("global-write-conflict", backstop.report().domain(), backstop.report().describe());
            assertEquals("other-writer", harness.stores.sharedStore().get("backstopKey"),
                    "the sneaking writer's committed value is retained; candidate write not published");
        }
    }

    // ==================== AC7：root 生命周期（保留 / 释放 / 独立 root 从空开始） ====================

    @Test
    void rootOwnedStateSurvivesReloadAndStopCycleAndIsReleasedByRootClose() throws Exception {
        Engine engine = Engine.newBuilder().build();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = testConfig();
        Marker marker = new Marker();
        GlobalStateStores standalone = new GlobalStateStores();
        StateTool state = new StateTool(standalone, marker);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(marker, state);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
        NekoRuntimeRoot root = new NekoRuntimeRoot(core, pluginRuntime, ScriptEventBridge.EMPTY,
                newPropertyRegistry(), sandboxFactory);
        try {
            Files.createDirectories(ScriptTypeEnv.scriptsDir(ScriptType.STARTUP));

            // 独立 root 从空状态开始
            assertTrue(root.globalState().storeFor(ScriptType.SERVER).isEmpty(),
                    "a fresh root must start with empty global state");
            assertTrue(root.globalState().sharedStore().isEmpty());

            // 先写脚本再 discover/load（loadScripts 消费 discover 的列表）
            Path serverDir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
            Files.writeString(serverDir.resolve("entry.js"),
                    "global.retained = (global.retained || 0) + 1; shared.anchor = 'root-one'");
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            assertEquals(1, root.globalState().storeFor(ScriptType.SERVER).get("retained"));

            // 普通 reload：generation close 不误清（非 guest 值保留）。entry.js 每次执行
            // 都会累加——reload 重跑了脚本，retained 1 → 2 本身就是「旧值可读且保留」的证据。
            root.reload(ScriptType.SERVER);
            assertEquals(2, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "generation close must not clear root-level state (reloaded script read the"
                            + " retained value and incremented it)");
            assertEquals("root-one", root.globalState().sharedStore().get("anchor"));

            // server stop / 切世界模拟：只清 WORLD 包监听（平台钩子语义），root 状态保留
            root.scriptManagerOf(ScriptType.SERVER).clearWorldPackListeners(List.of());
            root.scriptManagerOf(ScriptType.SERVER).discoverScripts();
            assertEquals(2, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "server stop / world switch must not clear global/shared");

            // 再次 reload：继续在保留值上累加（跨 reload 保留的脚本侧观察）
            root.reload(ScriptType.SERVER);
            assertEquals(3, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "reloaded scripts must see and keep incrementing the retained value");

            // root close 释放（close 后再获取 store 明确拒绝；已获取引用读到已释放的空状态）
            GlobalStore serverStoreRef = root.globalState().storeFor(ScriptType.SERVER);
            GlobalStore sharedStoreRef = root.globalState().sharedStore();
            root.closeSilently();
            assertTrue(root.globalState().isClosed(), "root close must release the state domain");
            assertTrue(sharedStoreRef.isEmpty(), "released stores must not retain entries");
            assertTrue(serverStoreRef.isEmpty(), "released stores must not retain entries");
            assertEquals("3-is-gone", sharedStoreRef.containsKey("anchor")
                    ? "still-there" : "3-is-gone");
            assertThrows(IllegalStateException.class,
                    () -> root.globalState().storeFor(ScriptType.SERVER),
                    "acquiring stores from a released root must be explicitly rejected");

            // 独立 root / 测试 runner 从空状态开始（互不可见、不串状态）
            Engine engine2 = Engine.newBuilder().build();
            DefaultErrorTracker tracker2 = new DefaultErrorTracker(paths, config);
            NekoCoreContext core2 = new NekoCoreContext(engine2, config, new ClassFilter(config), tracker2);
            NekoRuntimeRoot root2 = new NekoRuntimeRoot(core2, pluginRuntime, ScriptEventBridge.EMPTY,
                    newPropertyRegistry(), new NekoSandboxFactory(core2, paths,
                    ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime));
            try {
                assertTrue(root2.globalState().storeFor(ScriptType.SERVER).isEmpty(),
                        "a second independent root must start empty (no cross-root state bleed)");
                assertNull(root2.globalState().sharedStore().get("anchor"),
                        "the closed root's shared state must not leak into a new root");
            } finally {
                root2.closeSilently();
                engine2.close();
            }
        } finally {
            root.closeSilently();
            engine.close();
        }
    }

    // ==================== AC8：guest Value 生命周期 / 不承诺深回滚 ====================

    @Test
    void storedGuestFunctionsExpireWithTheirGenerationWhileHostValuesSurvive() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.fn = function () { return 42 }
                    global.helper = 7
                    Marker.hit('v1-fn:' + typeof global.fn)
                    """);
            harness.load(ScriptType.SERVER);
            assertEquals("v1-fn:function", harness.marker.first("v1-fn").orElseThrow());
            assertNotNull(harness.stores.storeFor(ScriptType.SERVER).get("fn"),
                    "guest function is stored while its generation is alive");

            // reload：候选执行期间旧 generation 仍存活——其已提交 guest 值对候选可读
            // （读已提交语义）；旧 generation 在 commit 点关闭，其 guest 值随之失效。
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    Marker.hit('candidate-sees-live-old-gen:' + (typeof global.fn))
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("candidate-sees-live-old-gen:function",
                    harness.marker.first("candidate-sees-live-old-gen").orElseThrow(),
                    "during candidate execution the old generation is still alive (committed reads)");
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("fn"),
                    "guest functions from the destroyed generation must expire at commit"
                            + " (no permanent keep-alive from being stored in the map)");

            // commit 后的观察点（新 active generation 内的 FILE reload 探针）：
            // guest 函数已失效，宿主标量值保留（跨 reload 的 root 级保留）
            harness.writeScript(ScriptType.SERVER, "probe.js", """
                    Marker.hit('v2-fn:' + (typeof global.fn))
                    Marker.hit('v2-helper:' + global.helper)
                    """);
            harness.managerOf(ScriptType.SERVER).reloadScriptFile("probe.js");
            assertEquals("v2-fn:undefined", harness.marker.first("v2-fn").orElseThrow(),
                    "post-commit reads must not see the destroyed generation's guest functions");
            assertEquals("v2-helper:7", harness.marker.first("v2-helper").orElseThrow(),
                    "host scalar values must survive reloads (root-level retention)");
        }
    }

    @Test
    void nestedMutationsAreNotDeepRolledBack() throws Exception {
        try (Harness harness = new Harness()) {
            // 同一 generation 内：file1 放嵌套对象，file2 原地改内层——嵌套修改不进写集，
            // 立即生效（明确不承诺深回滚）
            harness.writeScript(ScriptType.SERVER, "one.js", "global.box = { v: 1 }");
            harness.writeScript(ScriptType.SERVER, "two.js", """
                    global.box.v = 99
                    Marker.hit('nested-mutated:' + global.box.v)
                    """);
            harness.load(ScriptType.SERVER);
            assertEquals("nested-mutated:99", harness.marker.first("nested-mutated").orElseThrow());

            // 失败候选（只写顶层 key + 烧尽语句上限）：嵌套修改不被回滚
            harness.writeScript(ScriptType.SERVER, "one.js", """
                    global.doomed = 'cand'
                    while (true) { }
                    """);
            Files.delete(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("two.js"));
            assertThrows(NekoReloadException.class, () -> harness.reload(ScriptType.SERVER));
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("doomed"),
                    "top-level candidate write is discarded");

            // 同一 active generation 内的 FILE reload 探针读回嵌套值：仍是 99（未深回滚）
            Files.delete(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("one.js"));
            harness.writeScript(ScriptType.SERVER, "probe.js",
                    "Marker.hit('box-after-failed-candidate:' + global.box.v)");
            harness.managerOf(ScriptType.SERVER).reloadScriptFile("probe.js");
            assertEquals("box-after-failed-candidate:99",
                    harness.marker.first("box-after-failed-candidate").orElseThrow(),
                    "nested mutations are explicitly NOT deep-rolled-back by a failed candidate");
        }
    }

    // ==================== AC9：global 状态容器 vs globalThis 语言全局 ====================

    @Test
    void globalIsTheStateContainerWhileGlobalThisStaysTheLanguageGlobal() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.lang = 'container'
                    Marker.hit('division:' + (global !== globalThis))
                    globalThis.lang = 'language-global'
                    Marker.hit('container-value:' + global.lang)
                    Marker.hit('language-value:' + globalThis.lang)
                    global.x_on_container = 1
                    Marker.hit('globalThis-clean:' + (typeof globalThis.x_on_container === 'undefined'))
                    Marker.hit('container-owns-global-name:' + (globalThis.global === global))
                    Marker.hit('module-internals-use-globalThis:' + (typeof globalThis.require === 'function' && typeof globalThis.__nekoNodeResolve === 'function'))
                    Marker.hit('require-intact:' + typeof require)
                    """);
            harness.load(ScriptType.SERVER);

            assertEquals("division:true", harness.marker.first("division").orElseThrow(),
                    "in the full NekoJS environment, global (state container) must differ from globalThis");
            assertEquals("container-value:container", harness.marker.first("container-value").orElseThrow());
            assertEquals("language-value:language-global", harness.marker.first("language-value").orElseThrow(),
                    "globalThis keeps independent language-global properties");
            assertEquals("globalThis-clean:true", harness.marker.first("globalThis-clean").orElseThrow(),
                    "container writes must not leak onto globalThis");
            assertEquals("container-owns-global-name:true",
                    harness.marker.first("container-owns-global-name").orElseThrow(),
                    "the binding install takes over the 'global' property name on globalThis for the"
                            + " state container (same takeover as the pre-1.2.0 binding; the Node shim's"
                            + " global===globalThis alias only holds in shim-only contexts)");
            assertEquals("module-internals-use-globalThis:true",
                    harness.marker.first("module-internals-use-globalThis").orElseThrow(),
                    "Node shim / module install and resolution keep using the correct language global"
                            + " object (globalThis), not the state container");
            assertEquals("require-intact:function", harness.marker.first("require-intact").orElseThrow());
            assertEquals("container", harness.stores.storeFor(ScriptType.SERVER).get("lang"),
                    "script writes on global go to the NekoJS state container, not globalThis");
        }
    }

    // ==================== AC10：迁移（同类型不变 / 旧跨类型不再隐式可见） ====================

    @Test
    void sameTypeUsageUnchangedAndLegacyCrossTypeFallbackIsGone() throws Exception {
        try (Harness harness = new Harness()) {
            // 同类型用法保持不变（KubeJS 风格 global.foo 直读直写）
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.counter = (global.counter || 0) + 1
                    shared.cross = 'explicit'
                    """);
            harness.load(ScriptType.SERVER);
            harness.reload(ScriptType.SERVER);
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("counter"),
                    "same-type global.foo usage is unchanged across reloads");

            // 旧隐式跨类型回退已删除：CLIENT 读 global.cross 得 undefined（不再共享进程 Map），
            // 显式 shared 入口承载跨类型共享
            harness.writeScript(ScriptType.CLIENT, "entry.js", """
                    Marker.hit('legacy-implicit-gone:' + (typeof global.cross))
                    Marker.hit('explicit-shared:' + shared.cross)
                    """);
            harness.load(ScriptType.CLIENT);
            assertEquals("legacy-implicit-gone:undefined",
                    harness.marker.first("legacy-implicit-gone").orElseThrow(),
                    "the old implicit cross-type global fallback must not exist");
            assertEquals("explicit-shared:explicit",
                    harness.marker.first("explicit-shared").orElseThrow(),
                    "cross-type sharing goes through the explicit shared entry");
        }
    }

    // ==================== AC11：不越界（无第二 owner / 无 static 状态） ====================

    @Test
    void stateDomainHasNoStaticStateAndNoSecondOwner() throws Exception {
        // global 状态域完全 root 实例持有：core.state 包的关键类型不得有可变 static 字段
        // （否则就回归了进程级 NekoGlobal 的老问题）。权限系统/网络同步协议/通用事务框架
        // 的「不新增」由 guardLint + 代码走查承担（REPORT 记录），此处钉住最关键的
        // 「无 static 可变状态」不变式。
        for (Class<?> clazz : List.of(GlobalStateStores.class, GlobalStore.class,
                GenerationGlobals.class, GlobalView.class)) {
            for (Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                assertFalse(Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers),
                        clazz.getSimpleName() + "." + field.getName()
                                + " must not be mutable static state (root-owned domain only)");
            }
        }
    }
}
