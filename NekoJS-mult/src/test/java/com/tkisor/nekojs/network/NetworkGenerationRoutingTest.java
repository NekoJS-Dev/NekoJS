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
 * 票 17 网络自定义通道的 owner 队列路由与 generation/stale 边界 fixture：
 * 平台 enqueue/main-thread 入口之后的中立投递核心（{@link NetworkMessageHandler}）把
 * {@link NekoScriptPayload} 路由进对应 ScriptType 的 {@link NetworkEvents} 总线，
 * 再由票 06/07 的 candidate/commit/close/watchdog 机制约束接收面。
 *
 * <p>本测试在真实 {@link ScriptManager}（含 Graal Context、pendingListeners、commit 点、
 * close 优先、activeFailed 隔离）上经脚本回调观察，不读私有字段作契约断言（除测试探针
 * currentContext 取运行时引用用于「已关闭 Context 不被触碰」的反证）。平台主线程 hop
 * （enqueueWork / server().execute()）是 loader 侧行为，由 characterization 与源码 trace
 * 钉住，见 REPORT；本测试的调用线程即 owner 线程，与 SERVER=server thread、CLIENT=render
 * thread 的生产形态同构。
 */
class NetworkGenerationRoutingTest {

    // ---- 测试绑定对象 ----

    /** 注入脚本的计数器绑定：按 tag 分别计数，区分旧/新 generation 的回调来源。 */
    public static final class Recorder {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(tag);
            return value == null ? 0 : value.get();
        }
    }

    /** 注入脚本的 Java 侧触发器：脚本执行中途模拟「平台主线程上到达一个脚本通道包」。 */
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

    /** 绑定真实 {@link NetworkEvents} 组的 bridge：脚本可注册 server/client 网络监听器。 */
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
            // 与 bridge 持有同一组实例：schema 收割与 bindEvents 指向同一条总线
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

    // ---- 装配 ----

    @BeforeAll
    static void initPlatform() {
        // 与 common 测试树 TestPlatformInit 同形的 Platform 桩（src/test 无法引用 common 测试 fixture）；
        // 已初始化则跳过，避免污染同 JVM 的其它测试类。
        try {
            Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                // gameDir 按测试 JVM 唯一化（TestGameDirs 见统一说明）：并行 test JVM 共享
                // 固定名 tmp 目录会在 @BeforeEach 清脚本时互清 fixture——本类曾实测
                // 双 fabric 节点并行时偶发「候选脚本零执行」；另注意 Platform 初始化是
                // 先到先得，本类可能寄生在同 JVM 更早初始化的（同样已唯一化的）目录上。
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

    private static SandboxConfig withStatementLimit() {
        // 与 common ScriptReloadRegressionTest 相同的测试沙盒：5s 求值超时 + 100k 语句上限
        return new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
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
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                    ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
            ScriptEnvironmentFactory environmentFactory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory);
            this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, environmentFactory);
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

    /** 平台主线程入口后的中立投递：SERVER 包进 SERVER 总线、按 channel 定向、带 sender。 */
    @Test
    void serverPacketRoutesToActiveListenerByChannel() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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

            // 非法/未知 channel 的包是既有「无监听器即丢弃」语义：不抛、不炸调用方（平台主线程）
            assertDoesNotThrow(() -> NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("unknown_channel", new net.minecraft.nbt.CompoundTag()), null));
            assertEquals(0, harness.recorder.hitsOf("v1-other"));
        }
    }

    /** CLIENT 包只进 CLIENT 总线；SERVER 监听器不受 S2C 包影响（跨总线隔离）。 */
    @Test
    void clientPacketRoutesOnlyToClientBus() throws Exception {
        try (ManagerHarness serverHarness = new ManagerHarness(withStatementLimit())) {
            serverHarness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('server-l'); });
                    """);
            serverHarness.loadAndRun();

            try (ManagerHarness clientHarness = new ManagerHarness(withStatementLimit(), ScriptType.CLIENT)) {
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
     * AC4 前半：reload commit 前到达的 packet 由 active generation 服务，候选监听器
     * 挂起（pendingListeners）不提前进入生产路由；commit 后同一事件只由新 generation
     * 处理一次，旧 generation 不再接收。
     */
    @Test
    void candidatePhasePacketIsServedByActiveAndNeverEntersCandidate() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v1-l"));

            // 候选脚本先注册自己的监听器（进入挂起收集），再模拟「包到达」：
            // 此刻包必须仍由 active v1 服务，候选监听器不可见。
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

            // commit 后：新事件只由新 generation 处理一次，旧 generation 冻结
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

    /** 连续两次 commit 后只有最新 generation 接收（generation 单调推进，无旧代复活）。 */
    @Test
    void consecutiveReloadsKeepOnlyLatestGenerationReceiving() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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
     * AC5 root close 侧：close 清空该类型的网络监听器，在途 packet 被丢弃（无监听器命中、
     * 不抛异常），已关闭的 Context 不再被触碰（eval 抛 / 反查抛）——「不复活、不报错刷屏」。
     */
    @Test
    void closeDropsInFlightPacketsAndNeverTouchesClosedContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.postServerPacket();
            assertEquals(1, harness.recorder.hitsOf("v1-l"));

            Context closedContext = currentContext(harness.manager);
            harness.manager.close();

            // 在途 packet：丢弃而非异常（平台 enqueue 的 runnable 不炸）
            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(1, harness.recorder.hitsOf("v1-l"), "in-flight packet after close must be dropped");

            // 已关闭 Context 不被触碰的反证：eval 抛、ScriptManager 反查抛（close 后未复活）
            assertThrows(Exception.class, () -> closedContext.eval("js", "1 + 1"));
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(closedContext));
            assertFalse(harness.manager.isActiveFailed(), "clean close is not an isolated failure");
        }
    }

    /**
     * AC5 watchdog 隔离侧：active 被语句上限终止进入隔离失败（票 07 markActiveFailed）后，
     * 到达的 packet 被监听器闭包的 isContextDead 短路丢弃——不调用已关闭 Context、不抛异常、
     * 不自动重建第二个 active。
     */
    @Test
    void isolatedActiveFailureDropsInFlightPackets() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            // 监听器先注册成功，随后脚本失控烧尽语句预算：active Context 被终止、进入隔离失败，
            // 但监听器闭包仍在总线上（清扫只发生在 reload/close）
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    while (true) { }
                    """);
            harness.loadAndRun();

            assertTrue(harness.manager.isActiveFailed(),
                    "statement-limit kill of the active context must enter isolated failure");

            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(0, harness.recorder.hitsOf("v1-l"),
                    "in-flight packet must be dropped for the killed context, without dispatching into it");

            // 隔离失败不自建第二个 active：generation 不动，显式 reload 才恢复接收
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
     * AC6 网络线程异常等价防线：监听器执行体抛错被事件总线捕获记录，不传播给调用方
     * （平台 enqueue 的主线程任务不炸），且不中断同 channel 的其它监听器。
     */
    @Test
    void listenerExceptionIsContainedAndDoesNotAbortDispatch() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
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

    /** close 幂等 + close 后 packet 仍安全（与票 07 close 语义交叉的网络面观察）。 */
    @Test
    void repeatedCloseAndLatePacketsAreSafe() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(withStatementLimit())) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('ch', function (event) { Recorder.hit('v1-l'); });
                    """);
            harness.loadAndRun();
            harness.manager.close();
            assertDoesNotThrow(harness.manager::close, "close is idempotent");
            assertDoesNotThrow(harness::postServerPacket);
            assertEquals(0, harness.recorder.hitsOf("v1-l"));

            // 行为下界替代自证式字段复述：close 后再投一次仍安全、仍零投递
            assertDoesNotThrow(harness::postServerPacket, "late packets stay safe after repeated close");
            assertEquals(0, harness.recorder.hitsOf("v1-l"), "no listener may fire after close");
        }
    }
}
