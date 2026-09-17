package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.bindings.static_access.ClientDataJS;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.NekoScriptPayload;
import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import com.tkisor.nekojs.wrapper.pdata.PersistentDataJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 18 generation 边界与 reload 保留域 fixture（票 17 NetworkGenerationRoutingTest 的
 * harness 先例）：PData mirror 与 ClientData store 是<b>进程级</b>客户端状态——接收面
 * （acceptClientSync / ClientDataStore.accept）只写 Java 状态、从不进入任何 Graal Context，
 * 因此 SERVER/CLIENT reload、candidate 失败与 root close 都不触碰它们；清空只由平台
 * 域钩子（断线/离开旧世界）发起。脚本侧经绑定读取同一状态，reload 前后读取结果不变
 * （「NekoJS 自有 reload 不回滚世界副作用」的数据面投影）。
 *
 * <p>实体 pdata（服务端持久化容器）同样跨 reload 保留：脚本写入的容器数据随实体存档，
 * reload 只重建脚本环境——「SERVER reload 前后玩家/实体持久化数据与脚本读取结果对照」
 * 的 JVM 可达面在本测试钉住（真实实体/存档的 joinLevel 窗口与落盘见节点本地与
 * characterization）。
 */
class DataSyncGenerationBoundaryTest {

    // ---- 测试绑定对象 ----

    public static final class Recorder {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(String.valueOf(tag));
            return value == null ? 0 : value.get();
        }

        public void record(String tag, Object value) {
            hits.put(String.valueOf(tag), new AtomicInteger(value instanceof Number n ? n.intValue() : -1));
        }

        public int valueOf(String tag) {
            AtomicInteger value = hits.get(String.valueOf(tag));
            return value == null ? 0 : value.get();
        }
    }

    /** 实体 pdata 门面：把内存容器（id 面）暴露给脚本读写（生产面等价：EntityExtension#neko$pdata）。 */
    public static final class PDataFacade {
        private final int entityId;

        PDataFacade(int entityId) {
            this.entityId = entityId;
        }

        public CompoundTag tag() {
            return EntityPDataStore.get().get(entityId, EntityExtension.NEKO_PDATA_KEY);
        }

        /** 版本无关的整数读（26.x getIntOr / 1.21.1 getInt）。 */
        public int read(String key) {
//? if >=26 {
            return tag().getIntOr(key, -1);
//?} else {
/*            return tag().getInt(key);
*///?}
        }

        public void put(String key, int value) {
            CompoundTag tag = tag();
            tag.putInt(key, value);
            EntityPDataStore.get().set(entityId, EntityExtension.NEKO_PDATA_KEY, tag);
        }
    }

    static final class DataSyncEventBridge implements ScriptEventBridge {
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
        private final PDataFacade pdata;

        StubPluginRuntime(Recorder recorder, PDataFacade pdata) {
            this.recorder = recorder;
            this.pdata = pdata;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Recorder", Binding.of("Recorder", recorder),
                    "Pdata", Binding.of("Pdata", pdata),
                    "clientData", Binding.of("clientData", new ClientDataJS()));
        }

        @Override
        public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() {
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

    // ---- 装配（NetworkGenerationRoutingTest harness 同形复刻） ----

    @BeforeAll
    static void initPlatform() {
        try {
            Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                String dirId = "nekojs-datasynctest-" + Integer.toHexString(
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
    void cleanState() throws Exception {
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
        ClientDataStore.SHARED.clear();
        PDataSyncService.clearClientMirrors();
        EntityPDataStore.install(memoryAccess());
    }

    @AfterEach
    void sweepState() {
        NetworkEvents.GROUP.clearListeners(ScriptType.SERVER);
        NetworkEvents.GROUP.clearListeners(ScriptType.CLIENT);
        ClientDataStore.SHARED.clear();
        PDataSyncService.clearClientMirrors();
        EntityPDataStore.install(memoryAccess());
    }

    /** 内存容器（id 面；EntityPDataStoreTest 同形）：joinLevel 窗口语义不在本测试面（见 trace/节点 fixture）。 */
    private static EntityPDataStore.Access memoryAccess() {
        Map<Integer, CompoundTag> containers = new HashMap<>();
        return new EntityPDataStore.Access() {
            private CompoundTag container(int entityId) {
                return containers.computeIfAbsent(entityId, ignored -> new CompoundTag());
            }

            @Override
            public CompoundTag get(int entityId, String key) {
//? if >=26 {
                return container(entityId).getCompound(key).orElseGet(CompoundTag::new).copy();
//?} else {
/*                return container(entityId).getCompound(key).copy();
*///?}
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                CompoundTag container = container(entityId);
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
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

    private static final class ManagerHarness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final DataSyncEventBridge bridge = new DataSyncEventBridge();
        final Recorder recorder = new Recorder();
        final PDataFacade pdata = new PDataFacade(42);
        final ScriptManager manager;
        final ScriptType scriptType;

        ManagerHarness(ScriptType scriptType) {
            SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
            NekoJSPaths paths = NekoJSPaths.get();
            this.scriptType = scriptType;
            StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder, pdata);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                    ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
            ScriptEnvironmentFactory environmentFactory =
                    // ticket 10 起 ScriptEnvironmentFactory 需要 root 拥有的状态域（4 参构造器）；
                    // 本测试不覆盖 global/shared 面，传独立空 store（认领基线上此构造点漏适配，
                    // ticket 16 顺带修复——见 baseline REPORT「顺带修复」节）
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory,
                            new com.tkisor.nekojs.core.state.GlobalStateStores());
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

        void postServerProbe() {
            NetworkMessageHandler.postServerEvent(
                    new NekoScriptPayload("probe", new CompoundTag()), null);
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

    private static void pushClientData(String key, String json) {
        ClientDataStore.SHARED.accept(key, com.google.gson.JsonParser.parseString(json));
    }

    // ---- fixture ----

    /**
     * AC7 + reload 保留：SERVER reload 前后，ClientData store 与 pdata mirror（进程级客户端
     * 状态）保留，脚本读取结果不变——reload 只重建脚本环境，不触碰数据同步域。
     */
    @Test
    void serverReloadKeepsClientStoresAndScriptReadsStable() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            pushClientData("tick:counter", "303");
            harness.writeScript("entry.js", """
                    Recorder.record('read', clientData.get('tick:counter'));
                    NetworkEvents.server('probe', function () { Recorder.hit('l1'); });
                    """);
            harness.loadAndRun();
            assertEquals(303, harness.recorder.valueOf("read"), "v1 script must read the synced value");

            harness.writeScript("entry.js", """
                    Recorder.record('read2', clientData.get('tick:counter'));
                    NetworkEvents.server('probe', function () { Recorder.hit('l2'); });
                    """);
            harness.reload();
            assertEquals(303, harness.recorder.valueOf("read2"),
                    "v2 generation must read the same value after reload (store survives reload)");

            harness.postServerProbe();
            assertEquals(1, harness.recorder.hitsOf("l2"), "new generation serves events");
            assertEquals(0, harness.recorder.hitsOf("l1"), "old generation is swept");
        }
    }

    /** CLIENT 侧 reload（F3+T 资源重载同型）同样不清空 store。 */
    @Test
    void clientReloadKeepsClientStores() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.CLIENT)) {
            pushClientData("hud:mana", "77");
            harness.writeScript("entry.js", """
                    Recorder.record('v1', clientData.get('hud:mana'));
                    """);
            harness.loadAndRun();
            assertEquals(77, harness.recorder.valueOf("v1"));

            harness.writeScript("entry.js", """
                    Recorder.record('v2', clientData.get('hud:mana'));
                    """);
            harness.reload();
            assertEquals(77, harness.recorder.valueOf("v2"), "client data must survive CLIENT reload");
        }
    }

    /**
     * candidate 失败保留：candidate 被语句上限杀死（票 06/07 的事务失败路径，先例
     * transactionalReloadRejectsCandidateKilledByStatementLimit）→ reload 抛错、active
     * generation 原样存活——store 不丢、旧监听器继续可读。「失败只回退 NekoJS 自有脚本
     * 环境，不连带数据域」。（per-script 容错路径——坏脚本被跳过、其余脚本照常提交——
     * 是另一条既有语义，由 ScriptReloadRegressionTest.unreadableScript 钉住，不在本域。）
     */
    @Test
    void failedCandidateKeepsStoresAndActiveReads() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            pushClientData("tick:counter", "55");
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () {
                        Recorder.record('active-read', clientData.get('tick:counter'));
                        Recorder.hit('active-l');
                    });
                    """);
            harness.loadAndRun();

            // candidate 失控烧尽语句预算：事务 reload 按失败处理，active 保留
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () { Recorder.hit('candidate-l'); });
                    while (true) { }
                    """);
            assertThrows(RuntimeException.class, harness::reload,
                    "candidate killed by the statement limit must fail the reload");

            harness.postServerProbe();
            assertEquals(55, harness.recorder.valueOf("active-read"),
                    "active generation must still read the same value after a failed reload");
            assertEquals(1, harness.recorder.hitsOf("active-l"), "active listener survives the failed candidate");
            assertEquals(0, harness.recorder.hitsOf("candidate-l"), "candidate listener never activated");
        }
    }

    /**
     * root close 边界：close 只关脚本环境；接收面（acceptClientSync / ClientDataStore.accept）
     * 不依赖任何 Context——close 后到达的包仍安全写入、可读、不抛，已关闭 Context 不被触碰。
     * mirror/store 的正式清空只由平台域钩子（断线/离开旧世界）发起，不归 root close。
     */
    @Test
    void closeKeepsReceiveFaceSafeAndNeverTouchesClosedContext() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            harness.writeScript("entry.js", """
                    NetworkEvents.server('probe', function () { Recorder.hit('l1'); });
                    """);
            harness.loadAndRun();

            Context closedContext = currentContext(harness.manager);
            harness.manager.close();

            // close 后「在途包」到达接收面：安全、不抛、写入仍然生效（进程级状态）
            CompoundTag mirrorTag = new CompoundTag();
            mirrorTag.putInt("mana", 9);
            assertDoesNotThrow(() -> PDataSyncService.acceptClientSync(
                    new com.tkisor.nekojs.network.PDataSyncPacket(7, 1, mirrorTag)));
            assertDoesNotThrow(() -> pushClientData("late:key", "1"));
            assertTrue(PDataSyncService.hasPendingClientData(7), "mirror write after close is safe");
            assertEquals(1L, ClientDataStore.SHARED.get("late:key"), "client data write after close is safe");

            // 脚本回调面（进 Context 的面）随 close 消失：包被丢弃、已关闭 Context 不复活
            assertDoesNotThrow(harness::postServerProbe);
            assertEquals(0, harness.recorder.hitsOf("l1"), "no callback may fire after close");
            assertThrows(Exception.class, () -> closedContext.eval("js", "1 + 1"),
                    "closed context must never be touched again");
        }
    }

    /**
     * 「reload 不回滚世界副作用」数据面：脚本写入的实体 pdata（持久化容器，随实体存档）
     * 跨 reload 保留，脚本读取结果对照一致——reload 只重建脚本环境，不回滚容器数据。
     */
    @Test
    void serverReloadDoesNotRollBackEntityPData() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            harness.writeScript("entry.js", """
                    Pdata.put('ticket18', 303);
                    Recorder.record('v1', Pdata.read('ticket18'));
                    """);
            harness.loadAndRun();
            assertEquals(303, harness.recorder.valueOf("v1"));

            harness.writeScript("entry.js", """
                    Recorder.record('v2', Pdata.read('ticket18'));
                    """);
            harness.reload();
            assertEquals(303, harness.recorder.valueOf("v2"),
                    "entity pdata written by v1 must survive the reload and read back identically");
        }
    }

    /**
     * 接收面与脚本环境的正交性总查：accept 写入的 mirror 对脚本可见（经绑定面），
     * 且 revision 去重语义不受 reload 影响（域语义与 generation 无耦合）。
     */
    @Test
    void receiveFaceFeedsMirrorsIndependentOfGenerations() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.CLIENT)) {
            harness.writeScript("entry.js", """
                    Recorder.record('before', clientData.get('hud:mana'));
                    """);
            harness.loadAndRun();
            assertEquals(-1, harness.recorder.valueOf("before"),
                    "nothing synced yet: clientData.get reads back as script null (recorded as -1)");

            // 服务端推包到达（PData + ClientData 两域同批）
            CompoundTag mirrorTag = new CompoundTag();
            mirrorTag.putInt("mana", 12);
            PDataSyncService.acceptClientSync(new com.tkisor.nekojs.network.PDataSyncPacket(7, 2, mirrorTag));
            pushClientData("hud:mana", "12");

            harness.writeScript("entry.js", """
                    Recorder.record('after', clientData.get('hud:mana'));
                    """);
            harness.reload();
            assertEquals(12, harness.recorder.valueOf("after"), "synced data must be visible to the new generation");

            // stale revision 拒绝在 reload 后依旧成立（域语义不受 generation 影响）
            CompoundTag staleTag = new CompoundTag();
            staleTag.putInt("mana", 99);
            PDataSyncService.acceptClientSync(new com.tkisor.nekojs.network.PDataSyncPacket(7, 1, staleTag));
            assertEquals(mirrorTag, PDataSyncService.clientMirrorById(7),
                    "stale revision must stay rejected regardless of generation changes");
        }
    }

    /** PersistentDataJS 的服务端写入面（dirty/syncer 装配）在 reload 后仍指向同一容器。 */
    @Test
    void persistentDataJSKeepsServingTheSameContainerAcrossReloads() throws Exception {
        try (ManagerHarness harness = new ManagerHarness(ScriptType.SERVER)) {
            AtomicInteger dirty = new AtomicInteger();
            PersistentDataJS pdata = new PersistentDataJS(
                    () -> EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY),
                    tag -> EntityPDataStore.get().set(42, EntityExtension.NEKO_PDATA_KEY, tag),
                    dirty::incrementAndGet,
                    () -> {});
            pdata.putInt("k", 1);
            assertEquals(1, dirty.get());

            harness.reload(); // 无脚本改动的事务 reload 也走完整 candidate/commit

            pdata.putInt("k", 2);
            assertEquals(2, dirty.get(), "the write face keeps working after reload");
//? if >=26 {
            assertEquals(2, EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY).getIntOr("k", -1),
                    "and keeps hitting the same container");
//?} else {
/*            assertEquals(2, EntityPDataStore.get().get(42, EntityExtension.NEKO_PDATA_KEY).getInt("k"),
                    "and keeps hitting the same container");
*///?}
        }
    }
}
