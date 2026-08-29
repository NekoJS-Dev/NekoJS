package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ErrorTrackerReporter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindings;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.network.ScriptSyncService;
import com.tkisor.nekojs.platform.FabricIdCompat;
import com.tkisor.nekojs.platform.FabricPlatform;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 入口：脚本运行时 bring-up（P4-b）。
 *
 * <p>装配序与 NeoForge 侧 {@code NekoJSMod} 一致：插件发现 → V2 bootstrap →
 * 引擎上下文 / 沙盒工厂 / 运行时根 → STARTUP 脚本加载 → 通用注册表收集+抽干。
 * 两处 loader 差异：插件发现走 {@link FabricPluginLoader}（内置清单 + entrypoint）；
 * 注册表无逐 pass 事件，改 {@link FabricRegistryAdapter} 单批直注。
 *
 * <p>尚未接（随 LoaderBridge 后续批次）：chat / EntityEvents / ItemEvents 等余量事件、
 * 网络payload 通道（ScriptSync / pdata / clientData 推送）、
 * 自定义脚本事件（ScriptEvents，neoforge 实现面）、JEI 配方查看器、客户端专属装配。
 * 当前 fabric 脚本面 = 中性绑定 + 通用注册表（5 个平台无关类型）+ BlockEvents.broken
 * + ServerEvents 生命周期/tick + PlayerEvents 进出服（SERVER 脚本在 SERVER_STARTING 加载）。
 */
public final class NekoJSFabricMod extends NekoJS implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    public static NekoRuntimeRoot RUNTIME_ROOT;

    /** 客户端 tick 冲刷 CLIENT 侧 node timers（与 NeoForge 侧 NekoJSClient 同职责）。 */
    public static void flushClientNodeTimers() {
        if (RUNTIME_ROOT != null) {
            RUNTIME_ROOT.scriptManagerOf(com.tkisor.nekojs.api.ScriptType.CLIENT).flushReadyNodeTimers();
        }
    }

    static {
        Platform.init(new FabricPlatform());
        NekoIdCompat.init(new FabricIdCompat());
    }

    private final com.tkisor.nekojs.bindings.static_access.ScriptEventsJS scriptEventsRegistrar;

    public NekoJSFabricMod() {
        this(new com.tkisor.nekojs.bindings.static_access.ScriptEventsJS());
    }

    private NekoJSFabricMod(com.tkisor.nekojs.bindings.static_access.ScriptEventsJS scriptEventsRegistrar) {
        super(new DefaultScriptEventBridge(scriptEventsRegistrar));
        this.scriptEventsRegistrar = scriptEventsRegistrar;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("NekoJS fabric entrypoint reached.");
        LOGGER.info("  platform: loader={} v{}, mc={}, dev={}, client={}",
                Platform.instance().getLoaderId(),
                Platform.instance().getLoaderVersion(),
                Platform.instance().getMcVersion(),
                Platform.instance().isDevelopment(),
                Platform.instance().isClient());

        FabricBlockEventBindings.register();
        FabricEntityEventBindings.register();
        FabricServerEventBindings.register(() -> {
            if (RUNTIME_ROOT != null) {
                RUNTIME_ROOT.reload(com.tkisor.nekojs.api.ScriptType.SERVER);
            }
        });
        FabricPackSync.registerServer();
        FabricPlayNetwork.registerServer();
        initializeWorkspace();
        initializeScripts();
        FabricRegistryAdapter.onInitialize();
        LOGGER.info("NekoJS {} fabric bootstrap done (startup scripts loaded, registry drained).", NekoJS.MODID);
    }

    /** 与 NeoForge 侧 {@code NekoJSMod#initializeWorkspace} 同序：建目录 → 生成默认脚本 → 补目录 → 工作区配置。 */
    private static void initializeWorkspace() {
        NekoJSPaths paths = NekoJSPaths.get();
        paths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        paths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    /** 引擎装配：与 {@code NekoJSMod#initializeScripts} 同构，替换插件发现、去掉 neoforge 专属钩子。 */
    private void initializeScripts() {
        FabricPluginLoader.loadPlugins();
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrapOwned(
                NekoJSBasePluginManager.getOwnedPlugins(), this.scriptProperties);
        NekoRuntimeAccess.get().fireInit();
        ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
        this.scriptEventsRegistrar.bindRuntime(pluginRuntime);

        var compilers = ScriptCompilerRegistry.current();
        SandboxConfig sandboxConfig = ClassFilter.loadEngineConfig();
        ClassFilter classFilter = ClassFilter.INSTANCE;
        var errorTracker = new DefaultErrorTracker(NekoJSPaths.get(), sandboxConfig);
        ScriptErrorReporter.set(new ErrorTrackerReporter(errorTracker));
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(),
                sandboxConfig,
                classFilter,
                errorTracker
        );
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, NekoJSPaths.get(), compilers, pluginRuntime);
        NekoModulePipeline.bindLegacyInstance(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig));
        RUNTIME_ROOT = new NekoRuntimeRoot(
                core,
                pluginRuntime,
                this.scriptEventBridge,
                this.scriptProperties,
                sandboxFactory
        );
        ScriptSyncService.bindErrorTracker(core.errorTracker());

        for (ScriptType type : ScriptType.autoLoadTypes()) {
            var manager = RUNTIME_ROOT.createScriptManager(type);
            this.scriptManagers.set(type, manager);
            manager.discoverScripts();
        }

        this.scriptManagers.at(ScriptType.STARTUP).loadScripts();
        NekoRuntimeAccess.get().fireInitStartup();
    }
}
