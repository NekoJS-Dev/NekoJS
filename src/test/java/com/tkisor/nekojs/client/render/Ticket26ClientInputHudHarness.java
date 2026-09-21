//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 票 26 输入/HUD 域 fixture harness（版本树测试树）：真实 root + 真实 Graal 管线 + 真实
 * {@link ClientRenderRegistry}，脚本经 {@code ClientEvents.hudRender(...)} 注册常驻渲染器，
 * CLIENT 脚本的加载/reload 走 production 生命周期入口（{@code loadScripts} /
 * {@code root.reload(CLIENT)}）。
 *
 * <p>观测面刻意只用公开行为：{@link #dispatchHud(String)} 用 {@code List} 当渲染上下文的
 * 探针对象（{@code ctx.add(id)}），因此断言拿到的是「本帧真正被调用的渲染器 id」——
 * 而不是私有注册表的快照，也不是回调对象身份。候选期不可见 / commit 换装 / 失败保留旧
 * active 都由同一探针在 reload 前后各派发一次观察（票 26 AC4）。
 *
 * <p>形态与票 28 的 {@code PostEffectDeclarationHarness} 同构（同一装配序列、同一
 * Platform/gameDir 桩、同一 ScriptType = CLIENT），只是领域换成输入/HUD 注册面。
 */
public final class Ticket26ClientInputHudHarness implements AutoCloseable {

    /** 额外事件组（ClientEvents 承载 hudRender/hud，KeyBindEvents 承载 register/pressed/released/tick）。 */
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final Bridge bridge = new Bridge(groups);
    private final StubPluginRuntime pluginRuntime = new StubPluginRuntime(groups);

    public final Engine engine = Engine.newBuilder().build();
    public final NekoRuntimeRoot root;

    public Ticket26ClientInputHudHarness() {
        groups.put("ClientEvents", ClientEvents.GROUP);
        groups.put("KeyBindEvents", KeyBindEvents.GROUP);
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
        // 生产装配（NekoJSMod.registerClient 的 client dist 分支）同款：渲染器注册域的收集器
        // 必须注册，空批次才会走到 commit 点换装生产表。
        root.registerDomainCollector(new ClientRenderDomainOwner());
        root.createScriptManager(ScriptType.CLIENT).discoverScripts();
    }

    /** Platform 桩与 gameDir（测试树约定：{@code TestGameDirs.unique} 按 PID 隔离）。 */
    public static void ensurePlatformInitialized() {
        try {
            java.lang.reflect.Field instance = com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Path gameDir = com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket26-input-hud");
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
            throw new IllegalStateException("Failed to initialize Platform for ticket 26 client input/HUD tests", e);
        }
    }

    /** 写一份 CLIENT 脚本（同名覆盖）。 */
    public void writeClientScript(String name, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), source);
    }

    /** 非事务初始加载（生产由客户端 setup 触发：{@code NekoJSClient.onClientSetup}）。 */
    public void loadClientScripts() throws Exception {
        var manager = root.scriptManagerOf(ScriptType.CLIENT);
        manager.discoverScripts();
        manager.loadScripts();
    }

    /** 事务式 CLIENT reload（生产由 F3+T 资源 reload 触发）。 */
    public void reloadClientScripts() {
        root.reload(ScriptType.CLIENT);
    }

    /** 清空 CLIENT 脚本目录（每例独立，避免跨例残留）。 */
    public static void clearClientScripts() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.CLIENT);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 清空生产 HUD / 世界渲染器表，并释放 generation 身份（{@code clearAll()} 会一并置空
     * {@code activeContext}，故下一次注册按新 generation 换装）。独立用例因此互不污染；
     * 走既有公开清理面。
     */
    public static void resetRegistry() {
        ClientRenderRegistry.clearAll();
    }

    /**
     * 派发指定 HUD 层一帧，返回<b>真正被调用</b>的渲染器 id（按调用顺序）。探针是
     * {@code List}：脚本回调写 {@code ctx.add(id)}，因此这里观察到的是安装结果本身。
     */
    public static List<String> dispatchHud(String layer) {
        List<String> calls = new ArrayList<>();
        ClientRenderRegistry.dispatchHud(hudLayer(layer), calls, "gui");
        return calls;
    }

    /** 指定 HUD 层是否安装了存活渲染器（渲染钩子每帧的快路径判据）。 */
    public static boolean hasHud(String layer) {
        return ClientRenderRegistry.hasHud(hudLayer(layer));
    }

    private static ClientRenderRegistry.HudLayer hudLayer(String layer) {
        return switch (layer) {
            case "background" -> ClientRenderRegistry.HudLayer.BACKGROUND;
            case "normal" -> ClientRenderRegistry.HudLayer.NORMAL;
            case "foreground" -> ClientRenderRegistry.HudLayer.FOREGROUND;
            default -> throw new IllegalArgumentException("unknown hud layer " + layer);
        };
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
//?}
