package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.client.renderer.NekoNoopEntityRenderer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

public class NekoJSClient {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSClient::onClientSetup);
        modEventBus.addListener(NekoJSClient::onClientResourceReload);
        modEventBus.addListener(NekoJSClient::onRegisterEntityRenderers);
        NeoForge.EVENT_BUS.addListener(NekoJSClient::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(NekoJSClient::onLevelUnload);
        ClientEvents.bindModBus(modEventBus);
    }

    /// 某些事件需要极早期的时机，如RegisterKeyMappingsEvent
    private static void onClientSetup(FMLConstructModEvent event) {
        event.enqueueWork(() -> {
            NekoJS.LOGGER.debug("Client environment ready, loading CLIENT scripts...");
            NekoJSMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).loadScripts();
            ScriptType.CLIENT.logger().debug("Early script injection...");
        });
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityTypeRegistryEventJS.registeredEntityTypes().forEach(type -> event.registerEntityRenderer(type, NekoNoopEntityRenderer::new));
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        NekoJSMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).flushReadyNodeTimers();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PDataSyncService.clearClientMirrors();
        }
    }

    // 1.21.1: 事件名改为 RegisterClientReloadListenersEvent
    private static void onClientResourceReload(RegisterClientReloadListenersEvent event) {
        // 1.21.1: NeoForge 注册重载监听器不需要手动指定 ID，直接 registerReloadListener 即可
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                NekoJS.LOGGER.debug("CLIENT script reload failed", e);
            }
        });
    }
}