package com.tkisor.nekojs;

import com.mojang.logging.LogUtils;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.bindings.event.RegisterNekoJSPluginEvent;
import com.tkisor.nekojs.bindings.event.RegistryEvents;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NekoJSMemberRemapper;
import com.tkisor.nekojs.core.NekoJSScriptManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.ReflectionUtils;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import graal.mod.api.MemberRemapper;

import java.lang.reflect.Modifier;

@Mod(NekoJS.MODID)
public class NekoJS {
    public static final String MODID = "nekojs";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static IEventBus modEventBus;

    public static NekoJSScriptManager SCRIPT_MANAGER;


    public NekoJS(IEventBus modEventBus, ModContainer modContainer) {
        MemberRemapper.GLOBAL.set(new NekoJSMemberRemapper());

        NekoJS.modEventBus = modEventBus;

        registerEventListeners();

        SCRIPT_MANAGER = new NekoJSScriptManager();
        NekoJSPaths.initFoldersOnly();
        ScriptBootstrap.generateDefaultScripts();
        NekoJSPaths.initFoldersOnly();

        registerPlugins();

        SCRIPT_MANAGER.registerScriptProperty();

        LOGGER.info("[NekoJS] 正在执行 STARTUP 脚本...");
        SCRIPT_MANAGER.discoverScripts();
        SCRIPT_MANAGER.loadScripts(ScriptType.STARTUP);
//        SCRIPT_MANAGER.loadScripts(ScriptType.COMMON);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NekoJSClient.register(modEventBus);
        }
    }

    private void registerEventListeners() {
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::plugin);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerResourceReload);

        NeoForge.EVENT_BUS.addListener(NekoJSCommands::register);
        modEventBus.addListener(this::onRegister);
    }

    private void registerPlugins() {
        modEventBus.post(new RegisterNekoJSPluginEvent());
    }

    /**
     * 注册 NekoJS 插件
     * 扫描并注册所有实现了 NekoJSPlugin 接口的类
     */
    public void plugin(RegisterNekoJSPluginEvent event) {
        ReflectionUtils.findAnnotationClasses(
                RegisterNekoJSPlugin.class,
                null,
                clazz -> {
                    if (!NekoJSPlugin.class.isAssignableFrom(clazz)) {
                        LOGGER.error("[NekoJS] Plugin {} does not implement NekoJSPlugin", clazz.getName());
                        return;
                    }

                    int mod = clazz.getModifiers();
                    if (clazz.isInterface() || Modifier.isAbstract(mod)) {
                        LOGGER.error("[NekoJS] Plugin {} is not a concrete class", clazz.getName());
                        return;
                    }

                    try {
                        NekoJSPlugin plugin =
                                (NekoJSPlugin) clazz.getDeclaredConstructor().newInstance();
                        event.register(plugin);
                        LOGGER.info("[NekoJS] Registered plugin: {}", clazz.getName());
                    } catch (Throwable t) {
                        LOGGER.error("[NekoJS] Failed to instantiate plugin {}", clazz.getName(), t);
                    }
                },
                () -> LOGGER.info("[NekoJS] Plugin scan finished")
        );
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NekoJSPaths::createWorkspaceConfigs);
    }

    private void onServerStarting(ServerStartingEvent event) {
//        LOGGER.info("[NekoJS] 服务器启动，正在加载 SERVER 脚本...");
    }

    private void onServerResourceReload(AddServerReloadListenersEvent event) {
        try {
            NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("[NekoJS] 脚本重载失败: ", e);
        }
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            BlockRegistryEventJS eventJS = new BlockRegistryEventJS(event);

            RegistryEvents.BLOCK.post(eventJS);

            eventJS.registerAll();

        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            ItemRegistryEventJS eventJS = new ItemRegistryEventJS(event);

            RegistryEvents.ITEM.post(eventJS);

             eventJS.registerAll();

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.forEach((location, block) -> {
                event.register(Registries.ITEM, location, () -> {
                    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, location);
                    Item.Properties props = new Item.Properties().setId(key);

                    return new BlockItem(block, props);
                });
            });

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.clear();
        }
    }

}