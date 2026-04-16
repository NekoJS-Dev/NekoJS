package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

public class NekoJSClient {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSClient::onClientSetup);
        modEventBus.addListener(NekoJSClient::onClientResourceReload);
    }

    /// 某些事件需要极早期的时机，如RegisterKeyMappingsEvent
    private static void onClientSetup(FMLConstructModEvent event) {
        event.enqueueWork(() -> {
            NekoJS.LOGGER.info("[NekoJS] 客户端环境就绪，正在加载 CLIENT 脚本...");
            NekoJS.SCRIPT_MANAGER.loadScripts(ScriptType.CLIENT);
            ScriptType.CLIENT.logger().info("正在进行早期脚本注入...");
        });
    }

    private static void onClientResourceReload(AddClientReloadListenersEvent event) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.info("[NekoJS] 检测到客户端资源重载 (F3 + T)，正在重载 CLIENT 脚本...");
            try {
                NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.CLIENT);
            } catch (Exception e) {
                NekoJS.LOGGER.error("[NekoJS] ❌ CLIENT 脚本重载失败", e);
            }
        });
    }
}