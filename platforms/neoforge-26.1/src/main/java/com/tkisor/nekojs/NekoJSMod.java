package com.tkisor.nekojs;

import com.tkisor.nekojs.bindings.event.GoalEvents;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NekoJSMemberRemapper;
import com.tkisor.nekojs.core.NeoForgePluginLoader;
import com.tkisor.nekojs.core.NeoForgeRuntimeBootstrap;
import graal.mod.api.MemberRemapper;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.NeoForgeIdCompat;
import com.tkisor.nekojs.platform.NeoForgePlatform;
import com.tkisor.nekojs.platform.Platform;
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

    static {
        MemberRemapper.GLOBAL.set(new NekoJSMemberRemapper());
        Platform.init(new NeoForgePlatform());
        NekoIdCompat.init(new NeoForgeIdCompat());
    }

    public NekoJSMod(IEventBus modEventBus, ModContainer modContainer) {
        super(new DefaultScriptEventBridge(new ScriptEventsJS()));
        NekoJS.COMMON = this;
        NekoJSMod.modEventBus = modEventBus;

        long t0 = System.nanoTime();
        NeoForgeRuntimeBootstrap.setup();
        long t1 = System.nanoTime();
        registerEventListeners(modEventBus);
        long t2 = System.nanoTime();
        initializeWorkspace();
        long t3 = System.nanoTime();
        initializeScripts();
        long t4 = System.nanoTime();
        registerClient(modEventBus);
        long t5 = System.nanoTime();

        LOGGER.info("Bootstrap timings: setup={}ms events={}ms workspace={}ms scripts={}ms client={}ms total={}ms",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000,
                (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000,
                (t5 - t4) / 1_000_000, (t5 - t0) / 1_000_000);
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
        long s0 = System.nanoTime();
        NeoForgePluginLoader.loadAnnotatedPlugins();
        long s1 = System.nanoTime();
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrap(NekoJSBasePluginManager.getPlugins());
        long s2 = System.nanoTime();

        // 为每种自动加载的脚本类型创建 ScriptManager
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            var manager = new ScriptManager(this, type);
            this.scriptManagers.set(type, manager);
            manager.discoverScripts();
        }
        long s3 = System.nanoTime();

        // 只加载 STARTUP 类型
        this.scriptManagers.at(ScriptType.STARTUP).loadScripts();
        long s4 = System.nanoTime();
        GoalEvents.postRegister();

        LOGGER.info("Script init timings: plugins={}ms bootstrap={}ms discover={}ms load={}ms",
                (s1 - s0) / 1_000_000, (s2 - s1) / 1_000_000,
                (s3 - s2) / 1_000_000, (s4 - s3) / 1_000_000);
    }

    private static void registerClient(IEventBus modEventBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NekoJSClient.register(modEventBus);
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WorkspaceGenerator::createWorkspaceConfigs);
    }
}
