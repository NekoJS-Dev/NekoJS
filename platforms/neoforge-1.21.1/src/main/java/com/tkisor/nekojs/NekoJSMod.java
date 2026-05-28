package com.tkisor.nekojs;

import com.tkisor.nekojs.bindings.event.GoalEvents;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NeoForgePluginLoader;
import com.tkisor.nekojs.core.NeoForgeRuntimeBootstrap;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.listener.RegistryEventListener;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NekoJS.MODID)
public class NekoJSMod extends NekoJS {
    public static IEventBus modEventBus;

    public NekoJSMod(IEventBus modEventBus, ModContainer modContainer) {
        super(new DefaultScriptEventBridge(new ScriptEventsJS()));
        NekoJS.COMMON = this;
        NekoJSMod.modEventBus = modEventBus;

        NeoForgeRuntimeBootstrap.setup();
        registerEventListeners(modEventBus);
        initializeWorkspace();
        initializeScripts();
        registerClient(modEventBus);
    }

    private static void registerEventListeners(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSMod::onCommonSetup);
        modEventBus.addListener(RegistryEventListener::onRegister);
        modEventBus.addListener(RegistryEventListener::onEntityAttributeCreation);
        NeoForge.EVENT_BUS.addListener(NekoJSCommands::register);
        NeoForge.EVENT_BUS.addListener(RegistryEventListener::onEntityJoinLevel);
    }

    private static void initializeWorkspace() {
        NekoJSPaths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        NekoJSPaths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    private void initializeScripts() {
        NeoForgePluginLoader.loadAnnotatedPlugins();
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrap(NekoJSBasePluginManager.getPlugins());

        // 为每种自动加载的脚本类型创建 ScriptManager
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            var manager = new ScriptManager(this, type);
            this.scriptManagers.set(type, manager);
            manager.discoverScripts();
        }

        // 只加载 STARTUP 类型
        this.scriptManagers.at(ScriptType.STARTUP).loadScripts();
        GoalEvents.postRegister();
    }

    private static void registerClient(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NekoJSClient.register(modEventBus);
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WorkspaceGenerator::createWorkspaceConfigs);
    }
}
