//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 票 28 脚本路径 fixture harness（版本树测试树）：真实 root + 真实 Graal 管线 +
 * <b>真实</b> {@link PostEffectDomainOwner}（客户端 Adapter），脚本经
 * {@code ClientEvents.postEffects} 声明 register/unregister，候选收集/commit 点由
 * production 的 domain collector 接缝驱动。
 *
 * <p>形态与票 39 的 {@code Ticket39ModificationScriptHarness} 同构（同一装配序列、同一
 * Platform/gameDir 桩），只是 ScriptType 换成 CLIENT：CLIENT 是唯一有本域候选的
 * ScriptType，非事务初始加载由 {@link #loadAndApplyInitialPlan()} 显式模拟客户端主线程
 * 上的收集点。
 */
final class PostEffectDeclarationHarness implements AutoCloseable {

    /** 额外事件组（默认只挂 ClientEvents）。 */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    final Engine engine = Engine.newBuilder().build();
    final PostEffectDomainOwner owner = new PostEffectDomainOwner();
    final NekoRuntimeRoot root;

    PostEffectDeclarationHarness() {
        groups.put("ClientEvents", ClientEvents.GROUP);
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths,
                        compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, pluginRuntime, cache);
        root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        root.registerDomainCollector(owner);
        root.createScriptManager(ScriptType.CLIENT).discoverScripts();
    }

    /** Platform 桩与 gameDir（测试树约定：{@code TestGameDirs.unique} 按 PID 隔离）。 */
    static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket28-posteffects");
                gameDir.toFile().mkdirs();
                com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                    @Override public boolean isClient() { return true; }
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 28 post-effect tests", e);
        }
    }

    /** 写一份 CLIENT 脚本（同名覆盖）。 */
    void writeClientScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** 非事务初始加载 + 初始 generation 收集点（生产由客户端 setup 触发）。 */
    void loadAndApplyInitialPlan() throws Exception {
        var manager = root.scriptManagerOf(ScriptType.CLIENT);
        manager.discoverScripts();
        manager.loadScripts();
        owner.applyInitialPlan();
    }

    /** 事务式 CLIENT reload（生产由 F3+T 资源 reload 触发；DOMAIN_PLAN 收集 + commit）。 */
    void reloadClientScripts() {
        root.reload(ScriptType.CLIENT);
    }

    /** 清空 CLIENT 脚本目录（每例独立，避免跨例残留）。 */
    static void clearClientScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** 已安装定义 id 的字符串快照（只读查询面；不断言私有静态 Map）。 */
    static List<String> installedIds() {
        return PostEffectManager.installedDefinitions().keySet().stream()
                .map(String::valueOf).sorted().toList();
    }

    /**
     * 清空进程级已安装定义（独立用例互不污染）：走 production Adapter 的 installGeneration
     * 接缝，显式退役当前全部 id；不推进 owner 的 generation（用例从干净代开始）。
     */
    static void resetDeclarations() {
        java.util.Set<net.minecraft.resources.Identifier> installed =
                new java.util.LinkedHashSet<>(PostEffectManager.installedDefinitions().keySet());
        PostEffectManager.installGeneration(-1L, java.util.Map.of(), installed);
    }

    @Override
    public void close() {
        try {
            groups.values().forEach(group -> group.clearListeners(ScriptType.CLIENT));
            root.closeSilently();
        } finally {
            engine.close();
        }
    }

    /** 脚本面事件组绑定（生产形状：{@code EventGroupJS} 包每个组）。 */
    private static final class Bridge implements ScriptEventBridge {
        private final Map<String, EventGroup> groups;

        Bridge(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override
        public void bindEvents(graal.graalvm.polyglot.Value bindings, ScriptType type) {
            groups.forEach((name, group) -> bindings.putMember(name, new EventGroupJS(group, type)));
        }

        @Override
        public void clearListeners(ScriptType type) {
            groups.values().forEach(group -> group.clearListeners(type));
        }
    }

    /** 插件运行时桩：只广告事件组与空绑定面。 */
    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> groups;

        StubPluginRuntime(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of();
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return groups;
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
//?}
