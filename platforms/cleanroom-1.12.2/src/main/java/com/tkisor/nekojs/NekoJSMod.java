package com.tkisor.nekojs;

import com.tkisor.nekojs.bindings.event.CommandEvents;
import com.tkisor.nekojs.bindings.event.GoalEvents;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.ForgePluginLoader;
import com.tkisor.nekojs.core.ForgeRuntimeBootstrap;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ErrorTrackerReporter;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.ForgeIdCompat;
import com.tkisor.nekojs.platform.ForgePlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.listener.RegistryEventListener;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.FMLCommonHandler;

// version 必须手动跟随 gradle.properties 的 mod_version（注解只接受编译期常量，无法引用属性值）
@Mod(modid = NekoJS.MODID, name = "NekoJS", version = "1.1.0-preview2")
public class NekoJSMod extends NekoJS {
    public static EventBus modEventBus;
    public static NekoRuntimeRoot RUNTIME_ROOT;
    private final ScriptEventsJS scriptEventsRegistrar;

    static {
        Platform.init(new ForgePlatform());
        NekoIdCompat.init(new ForgeIdCompat());
    }

    public NekoJSMod() {
        this(new ScriptEventsJS());
    }

    private NekoJSMod(ScriptEventsJS scriptEventsRegistrar) {
        super(new DefaultScriptEventBridge(scriptEventsRegistrar));
        this.scriptEventsRegistrar = scriptEventsRegistrar;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ForgeRuntimeBootstrap.setup();
        registerEventListeners();
        com.tkisor.nekojs.network.NekoJSNetwork.init();
        initializeWorkspace();
        initializeScripts();
        WorkspaceGenerator.createWorkspaceConfigs();
        registerClient();
    }

    private static void registerEventListeners() {
        modEventBus = MinecraftForge.EVENT_BUS;
        // Server starting event for command registration
        MinecraftForge.EVENT_BUS.register(NekoJSCommands.class);
        // Register registry event listener
        MinecraftForge.EVENT_BUS.register(RegistryEventListener.class);
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        // 激活存档侧世界脚本包（<world>/nekojs_packs/）：必须在 SERVER reload 之前，
        // 这样包内脚本随同一次 reload 纳入，无需二次重载
        activateWorldPacks();
        // SERVER 脚本在最早的服务器生命周期事件加载，确保脚本能监听后续所有服务器事件
        //（starting/started/tick/recipes 等）。每次服务器启动都重新加载
        //（单人游戏每次开关世界、专用服务器每次启停）。
        try {
            if (RUNTIME_ROOT != null) {
                RUNTIME_ROOT.reload(ScriptType.SERVER);
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to load SERVER scripts on server about-to-start", e);
        }
        ServerEvents.ABOUT_TO_START.post(event);
        // tag 事件：脚本加载后触发一次，让 ore dict 修改在配方注册前生效
        // （1.12.2 OreDictionary 适配，dispatch 键 'ore_dict'）
        try {
            TagEventJS tagEvent = new TagEventJS();
            ServerEvents.TAGS.post(tagEvent, TagEventJS.REGISTRY_KEY);
            tagEvent.apply();
        } catch (Throwable e) {
            LOGGER.error("Failed to apply tag scripts", e);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        NekoJSCommands.registerCommands(event);
        ServerEvents.STARTING.post(event);
        CommandEvents.REGISTER.post(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        ServerEvents.STARTED.post(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerEvents.STOPPING.post(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerEvents.STOPPED.post(event);
        deactivateWorldPacks();
    }

    private static void activateWorldPacks() {
        try {
            java.io.File saveRoot = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
            if (saveRoot == null) return;
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get()
                    .activateWorldPacks(saveRoot.toPath());
        } catch (Throwable t) {
            LOGGER.warn("Failed to activate world script packs", t);
        }
    }

    /**
     * 卸载世界脚本包：按包前缀反注册 SERVER 监听器与 timer；客户端同侧清理
     * （客户端脚本在下次进世界时整体重载，这里只清监听器，避免返回标题界面后
     * 世界包回调继续在菜单 tick 上触发）。
     */
    private static void deactivateWorldPacks() {
        try {
            var removed = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().deactivateWorldPacks();
            if (removed.isEmpty()) return;
            if (RUNTIME_ROOT != null) {
                var serverManager = RUNTIME_ROOT.scriptManagerOrNull(ScriptType.SERVER);
                if (serverManager != null) serverManager.clearWorldPackListeners(removed);
                if (com.tkisor.nekojs.platform.Platform.isClient()) {
                    var clientManager = RUNTIME_ROOT.scriptManagerOrNull(ScriptType.CLIENT);
                    if (clientManager != null) clientManager.clearWorldPackListeners(removed);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to deactivate world script packs", t);
        }
    }

    private static void initializeWorkspace() {
        NekoJSPaths paths = NekoJSPaths.get();
        paths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        paths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    private void initializeScripts() {
        ForgePluginLoader.loadAnnotatedPlugins();
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrapOwned(
                NekoJSBasePluginManager.getOwnedPlugins(), this.scriptProperties);
        NekoRuntimeAccess.get().fireInit();
        scriptEventsRegistrar.bindRuntime(pluginRuntime);
        ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);

        var compilers = ScriptCompilerRegistry.current();
        SandboxConfig sandboxConfig = ClassFilter.loadEngineConfig();
        // 复用全局单例（NekoSecurityWarningHandler 等读取 ClassFilter.INSTANCE），避免双实例状态分裂
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
        NekoModulePipeline.bindLegacyInstance(new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig));
        RUNTIME_ROOT = new NekoRuntimeRoot(
                core,
                pluginRuntime,
                this.scriptEventBridge,
                this.scriptProperties,
                sandboxFactory
        );

        for (ScriptType type : ScriptType.autoLoadTypes()) {
            var manager = RUNTIME_ROOT.createScriptManager(type);
            this.scriptManagers.set(type, manager);
            manager.discoverScripts();
        }

        this.scriptManagers.at(ScriptType.STARTUP).loadScripts();
        NekoRuntimeAccess.get().fireInitStartup();
        GoalEvents.postRegister();
        // SERVER scripts also load at preInit so ServerEvents.recipes listeners are registered
        // before RegistryEvent.Register<IRecipe> fires (registry still unfrozen then).
        // serverAboutToStart reloads SERVER again for the rest of the server lifecycle.
        this.scriptManagers.at(ScriptType.SERVER).loadScripts();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        NekoRuntimeAccess.get().fireAfterInit();
        // Reliable recipe-script trigger: by postInit the IRecipe registry is fully populated
        // and still unfrozen (freezes at LoadComplete), so script-defined recipes register here.
        // RegistryEventListener.onRegisterRecipe may fire earlier on platforms that dispatch
        // RegistryEvent.Register<IRecipe>; applyRecipeScripts is idempotent.
        RegistryEventListener.applyRecipeScripts();
    }

    private static void registerClient() {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            NekoJSClient.register();
        }
    }
}
