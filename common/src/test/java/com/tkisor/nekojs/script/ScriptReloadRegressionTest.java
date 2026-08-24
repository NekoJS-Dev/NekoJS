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
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：/nekojs reload 对「非入口 helper 模块」的真实生效性。
 *
 * <p>背景缺陷：{@code ScriptManager.reloadScriptFile} 曾走 {@code hotReloadModule} 捷径，
 * 其 relink 因 revision 不匹配静默跳过全部模块却返回 success=true，导致 helper 修改后
 * reload 显示成功但完全不生效（见 ModuleSliceRelinker 的删除历史）。本测试固定修复后的
 * 契约：修改 helper 后 reload 必须让所有受影响的入口重新执行并观察到新值。
 */
class ScriptReloadRegressionTest {

    /** 记录入口脚本观察到的 helper 导出值；作为全局绑定注入沙盒。 */
    public static final class TestRecorder {
        private volatile String value;
        private volatile Context recordedContext;
        private volatile boolean contextDead;
        private volatile Context deadContext;
        private volatile ScriptManager manager;
        private volatile boolean managerContextMatches;
        private volatile boolean managerKilled;
        private volatile Context managerContextAtProbe;

        public void record(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        /** 记录「调用此方法的 JS 函数」所属的 Graal Context，用于断言脚本实际执行在哪个环境。 */
        public void recordContext(Value fn) {
            this.recordedContext = fn.getContext();
        }

        public Context recordedContext() {
            return recordedContext;
        }

        /** 记录当前 JS 函数所属 Context 是否被 ScriptManager.isContextDead 判为 dead。 */
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
                } catch (Exception ignored) {
                    // 诊断辅助：反射失败不应掩盖原始断言
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

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final TestRecorder recorder;

        StubPluginRuntime(TestRecorder recorder) {
            this.recorder = recorder;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", Binding.of("TestRecorder", recorder));
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
        // 使用固定的非临时目录：NekoJSLoggers 的 FileAppender 会持有 <gameDir>/logs/nekojs/server.log
        // 的文件句柄直到 JVM 退出；若把 gameDir 放在 JUnit @TempDir 中，类结束时临时目录清理会
        // 因文件被占用而在 Windows 上失败（executionError）。
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    /** 测试共享同一个测试 gameDir：每个用例前清空脚本目录，避免用例之间互相发现残留脚本。 */
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
     * C1 回归：事务式 reload 必须在候选 Context 中加载脚本，而不是沿用旧的
     * {@code this.context}/{@code this.nodeRuntime}。
     *
     * <p>脚本通过 {@code TestRecorder.recordContext((event) => {})} 把「定义该函数的
     * Graal Context」上报给 Java 侧。reload 成功后，该 Context 必须等于 ScriptManager
     * 当前持有的候选 Context；修复前脚本跑在旧 Context 中，二者不相等。
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
     * I1 回归：成功的完整 reload 必须清除 {@code contextKilled}；候选加载失败时恢复旧值。
     * 这里覆盖成功路径（失败路径由候选-kill 检测交给 catch 恢复）。
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
     * W1 回归：preload 读失败（编码损坏/权限问题）的脚本不得静默消失。
     * 旧行为是 {@code disabled=true} 后 {@code shouldRun()} 跳过、{@code lastError} 无人读——
     * 日志与错误面板都没有任何痕迹。修复后必须进 ErrorTracker 且异常类型保留。
     */
    @Test
    void unreadableScriptSurfacesInErrorTrackerInsteadOfVanishing() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path broken = serverDir.resolve("broken-encoding.js");
        // 0x80/0x81 是非法 UTF-8 序列：Files.newBufferedReader 的默认 UTF-8 解码在读到时
        // 抛 MalformedInputException，覆盖 preload 的 catch(Exception) 路径
        Files.write(broken, new byte[]{(byte) 0x80, (byte) 0x81, '\n'});

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L, 0);
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

    /**
     * C1/I1 回归：候选加载期间语句上限杀死候选 Context 时，事务式 reload 必须按失败
     * 处理——关闭候选、保留旧 Context，并把 {@code contextKilled} 恢复为 reload 前的值。
     * 即使 reload 前 {@code contextKilled} 已经是 true，也不能把死掉的候选提交为 live。
     */
    @Test
    void transactionalReloadRejectsCandidateKilledByStatementLimitWhenPreviousKilledIsTrue() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.js");
        Files.writeString(entry, "TestRecorder.record('v1');\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must succeed");

            Context oldContext = currentContext(manager);
            setContextKilled(manager, true);

            Files.writeString(entry, "while (true) { /* spin forever */ }\n");
            assertThrows(RuntimeException.class, manager::reloadScripts,
                    "reload must fail when the candidate context is killed by the statement limit");
            assertTrue(isContextKilled(manager),
                    "failure path must restore previousKilled=true instead of committing the dead candidate");
            assertEquals(oldContext, currentContext(manager),
                    "failed transactional reload must keep the old context as current");
        } finally {
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * C1 回归：事务式 reload 候选加载期间，候选 Context 的 timer 回调不能被判定为 dead。
     * 修复前 {@code isContextDead(candidateContext)} 在最终切换前看到的是旧 Context，
     * {@code await new Promise(r => setTimeout(r, 0))} 的回调会被跳过，候选脚本加载超时。
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
        // 短超时让修复前的 RED 在 5 秒内失败，而不是等满生产默认的 30 秒。
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 0, 0);
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
            assertTimeoutPreemptively(Duration.ofSeconds(10), manager::reloadScripts,
                    "candidate timer callback must not be skipped as dead");
            assertEquals(currentContext(manager), recorder.deadContext(),
                    "isContextDead probe must run in the candidate context");
            assertTrue(recorder.managerContextMatches(),
                    "diagnostic: manager.context should be identical to the probe context during candidate load; "
                            + "probe=" + recorder.deadContext() + " managerAtProbe=" + recorder.managerContextAtProbe()
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
     * 回归测试：getOrCreateContext 的 check-then-act 必须原子化。
     *
     * <p>修复前该方法未同步，多个线程并发通过空检查后会各自创建 Graal Context，
     * 输家 Context（及其 Node timer 调度线程）泄漏在 {@link ScriptManager#CONTEXT_TO_MANAGER}。
     * 由于现有公开 API 中唯一不持实例锁的 {@link ScriptManager#flushReadyNodeTimers()}
     * 并不创建 Context，本测试通过反射并发调用私有的 getOrCreateContext（测试允许反射，
     * 与既有测试一致）来固定该契约：每个 fresh manager 只留下一个 Context 条目。
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
                // 每个 round 使用独立 Engine：Graal 要求共享 Engine 的所有 Context 使用同一
                // HostAccess 配置，而 newManager 每次都会构建新的 NekoSharedHostAccess。
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
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory);
        return new ScriptManager(ScriptType.SERVER, eventBridge, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory);
    }

    private static Context currentContext(ScriptManager manager) throws Exception {
        // context 与 nodeRuntime/流已收进成对发布的 RuntimeEnvironment record（单个 volatile
        // 引用）；从这里取 context 等价于旧字段直读
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

    /**
     * 回归测试：顶层死循环（CJS 入口体在服务器线程同步执行）不得无限冻结服务器线程。
     * 修复契约：超过 scriptEvaluationTimeoutSeconds 后通过 Context.interrupt 注入中断，
     * 脚本按失败处理，loadScripts 正常返回。
     */
    @Test
    void infiniteLoopInScriptEntryDoesNotFreezeServerThread() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        // 语句上限 100_000（测试用）：死循环迅速烧尽 → Graal 关闭 Context 并中断当前求值，
        // 服务器线程恢复；生产默认 0（禁用），整合包服务器可按需在 nekojs/config/engine.toml 开启
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            manager.discoverScripts();

            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "infinite loop must be aborted by the statement limit");
            assertTrue(tracker.hasErrors(), "statement-limit failure must be recorded");

            // Context 已被 Graal 关闭：下一次取用必须自动重建，脚本环境保持可用
            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "context must be rebuilt automatically after the statement limit closed it");
        } finally {
            // 修复前（RED）阶段：被 assertTimeoutPreemptively 放弃的求值线程仍卡在死循环里，
            // 强制关闭存活 Context 防止测试 JVM 无法退出。
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * 回归测试：语句上限 kill 后的自动 Context 重建（getOrCreateContext 的重建分支）
     * 必须先清空本 scriptType 的事件监听器（与事务式 reload / fullReloadCleanup 顺序一致）。
     * 修复前重建分支只关闭旧环境而不清 listener，残留监听器闭包指向已死 Context，
     * 之后每次事件分发都会在死环境上报错，直到手动 /nekojs reload。
     */
    @Test
    void rebuildAfterStatementLimitKillClearsEventListeners() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        RecordingEventBridge bridge = new RecordingEventBridge();
        // 直接在声明处构造：lambda 捕获要求 manager 为实际上的 final 变量
        ScriptManager manager = newManager(paths, recorder, engine, config, tracker, bridge);
        try {
            manager.discoverScripts();
            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "initial load must abort via the statement limit");
            assertTrue(bridge.typeOnlyCleared.isEmpty(),
                    "first (fresh) environment creation must not clear listeners");

            // 下一次取用 Context 触发自动重建：重建分支必须清空本 scriptType 的全部监听器
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> manager.reloadScriptFile("loop.js"));
            assertTrue(bridge.typeOnlyCleared.contains(ScriptType.SERVER),
                    "context rebuild after statement-limit kill must clear this scriptType's listeners");
        } finally {
            forceCloseLiveContexts();
            manager.close();
            engine.close();
        }
    }

    /** 记录 clearListeners 调用的桥接桩：区分「整类型清除」（kill 后自动重建路径）与「按脚本清除」（单文件重载路径）。 */
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
