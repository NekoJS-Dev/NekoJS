package com.tkisor.nekojs;

import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NeoForgePluginLoader;
import com.tkisor.nekojs.core.NeoForgeRuntimeBootstrap;
import com.tkisor.nekojs.core.NekoJSScriptManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.listener.RegistryEventListener;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NekoJSCommon.MODID)
public class NekoJS extends NekoJSCommon {
    public static IEventBus modEventBus;
    public static NekoJSScriptManager SCRIPT_MANAGER;

    public NekoJS(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgeRuntimeBootstrap.setup();
        NekoJS.modEventBus = modEventBus;

        registerEventListeners(modEventBus);
        initializeWorkspace();
        initializeScripts();
        registerClient(modEventBus);
    }

    private static void registerEventListeners(IEventBus modEventBus) {
        modEventBus.addListener(NekoJS::onCommonSetup);
        modEventBus.addListener(RegistryEventListener::onRegister);
        NeoForge.EVENT_BUS.addListener(NekoJSCommands::register);
    }

    private static void initializeWorkspace() {
        NekoJSPaths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        NekoJSPaths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    private static void initializeScripts() {
        NeoForgePluginLoader.loadAnnotatedPlugins();

        SCRIPT_MANAGER = new NekoJSScriptManager();
        SCRIPT_MANAGER.registerScriptProperty();
        SCRIPT_MANAGER.discoverScripts();
        SCRIPT_MANAGER.loadScripts(ScriptType.STARTUP);
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
