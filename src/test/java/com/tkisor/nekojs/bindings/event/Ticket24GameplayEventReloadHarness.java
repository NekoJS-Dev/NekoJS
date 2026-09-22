//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
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
 * 票 24 脚本路径 reload harness（版本树测试树，NeoForge 侧）：真实 root + 真实 Graal 管线 +
 * 真实家族事件组（静态单例），脚本经 {@code PlayerEvents/LevelEvents/CommandEvents/...} 注册
 * 监听器，SERVER 的加载/reload 走生产生命周期入口（{@code loadScripts} / {@code root.reload}）。
 *
 * <p>与票 26/39 的 harness 同构（同一装配序列、同一 Platform/gameDir 桩）；本 harness 的
 * 家族是 gameplay 事件组，且在构造器<b>第一行</b>先装 cancellability predicate（生产
 * {@code NekoJSMod} 构造器次序），保证可取消家族总线不被冻错。无 vanilla 注册表依赖：
 * 脚本只挂无 key 监听器（keyed 注册需要真实 {@code Item}/{@code EntityType} 值），本
 * harness 的用例因此能（也只在）无头 JVM 真跑。
 */
final class Ticket24GameplayEventReloadHarness implements AutoCloseable {

    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);
    /** 生产 bridge（prepare/finish 批次换装），不是接口的 legacy fallback。 */
    private final com.tkisor.nekojs.core.DefaultScriptEventBridge bridge =
            new com.tkisor.nekojs.core.DefaultScriptEventBridge(null);

    final Engine engine = Engine.newBuilder().build();
    final NekoRuntimeRoot root;

    Ticket24GameplayEventReloadHarness() {
        // production init order: predicate first, family class-init afterwards
        EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
        NeoForgeBlockEvents.bootstrap();
        groups.put("BlockEvents", BlockEvents.GROUP);
        groups.put("ItemEvents", ItemEvents.GROUP);
        groups.put("LevelEvents", LevelEvents.GROUP);
        groups.put("PlayerEvents", PlayerEvents.GROUP);
        groups.put("CommandEvents", CommandEvents.GROUP);
        groups.put("CapabilityEvents", CapabilityEvents.GROUP);
        groups.put("GoalEvents", GoalEvents.GROUP);
        groups.put("EntityEvents", EntityEvents.GROUP);
        bridge.setPluginRuntime(pluginRuntime);
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        root.createScriptManager(ScriptType.STARTUP).discoverScripts();
        root.createScriptManager(ScriptType.SERVER).discoverScripts();
    }

    /** Platform 桩与 gameDir（测试树约定：{@code TestGameDirs.unique} 按 PID 隔离）。 */
    static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket24-reload");
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 24 gameplay reload tests", e);
        }
    }

    /** 写一份脚本（同名覆盖）。 */
    void writeScript(ScriptType type, String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(type);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** 非事务初始加载（生产由 server about-to-start / startup 构造期触发）。 */
    void loadScripts(ScriptType type) {
        var manager = root.scriptManagerOf(type);
        manager.discoverScripts();
        manager.loadScripts();
    }

    /** 事务式 reload（生产由 /nekojs reload server 触发）。 */
    void reloadScripts(ScriptType type) {
        root.reload(type);
    }

    /** 清空指定类型脚本目录（每例独立，避免跨例残留）。 */
    static void clearScripts(ScriptType type) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(type);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Override
    public void close() {
        try {
            groups.values().forEach(group -> {
                group.clearListeners(ScriptType.SERVER);
                group.clearListeners(ScriptType.STARTUP);
            });
            root.closeSilently();
        } finally {
            engine.close();
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
