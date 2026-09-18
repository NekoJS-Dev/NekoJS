package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.ItemEvents;
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
 * 票 39 脚本路径 E2E harness（版本树测试树，五节点共享同一份代码；测试本身 registry-gated，
 * 仅在 vanilla 注册表可用的环境真跑）：真实 root + 真实 Graal 管线 +
 * <b>真实</b> {@link ModificationDomainOwner}（平台 Adapter），脚本经
 * {@code ItemEvents.modification} 事件面收集声明、commit 点应用到真实 Item/Block。
 *
 * <p>与 common 层 {@code Ticket39DomainCollectionTest}（合成 Adapter）分工：本 harness
 * 覆盖「脚本事件 → 平台 Adapter 可观察结果」的贯穿面（AC13），common 那份覆盖 root 拥有的
 * 收集挂载点与联合边界机制。断言只走公开 seam（脚本、reload 成败、Item 组件读回、
 * {@code owner.lastDiagnostics()}）。
 *
 * <p>平台桩与 gameDir 遵循测试树约定（{@code TestPlatformInit} +
 * {@code TestGameDirs.unique}），独立测试 root 不与他例互相污染。
 */
final class Ticket39ModificationScriptHarness implements AutoCloseable {

    /** 额外事件组（block 半边在 26.x 测试里传入 {@code BlockEvents.GROUP}）。 */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    final Engine engine = Engine.newBuilder().build();
    final ModificationDomainOwner owner = new ModificationDomainOwner();
    final NekoRuntimeRoot root;

    Ticket39ModificationScriptHarness(Map<String, EventGroup> extraGroups) {
        groups.put("ItemEvents", ItemEvents.GROUP);
        groups.putAll(extraGroups);
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
        root.createScriptManager(ScriptType.SERVER).discoverScripts();
    }

    /** 平台桩与 gameDir（测试树约定：{@code TestGameDirs.unique} 按 PID 隔离，见票 17）。 */
    static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket39-e2e");
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 39 script E2E tests", e);
        }
    }

    /** 写一份 server 脚本（同名覆盖）。 */
    void writeServerScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** 初始 generation 的收集点（生产由 server about-to-start 触发）。 */
    void applyInitialPlan() {
        owner.applyInitialPlan(null);
    }

    /** 清空 server 脚本目录（每例独立，避免跨例残留）。 */
    static void clearServerScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
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
            groups.values().forEach(group -> group.clearListeners(ScriptType.SERVER));
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
