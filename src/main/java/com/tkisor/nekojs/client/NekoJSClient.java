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
            NekoJS.LOGGER.debug("[NekoJS] Client environment ready, loading CLIENT scripts...");
            NekoJS.SCRIPT_MANAGER.loadScripts(ScriptType.CLIENT);
            ScriptType.CLIENT.logger().debug("Early script injection...");
        });
    }

    private static void onClientResourceReload(AddClientReloadListenersEvent event) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("[NekoJS] Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.CLIENT);
            } catch (Exception e) {
                NekoJS.LOGGER.debug("[NekoJS] CLIENT script reload failed", e);
            }
        });
    }
}