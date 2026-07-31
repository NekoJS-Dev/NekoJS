package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.client.renderer.NekoNoopEntityRenderer;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

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

    private static void onClientResourceReload(AddClientReloadListenersEvent event) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                NekoJS.LOGGER.debug("CLIENT script reload failed", e);
            }
            postClientGeneration();
        });
    }

    /**
     * 客户端生成事件：脚本把 asset JSON 写入 {@code <gameDir>/nekojs/assets}（磁盘 resource
     * pack，懒读保证 reload 时序正确）。先聚合 lang 再生成 assets，与 KubeJS 流程一致。
     */
    private static void postClientGeneration() {
        try {
            Path assets = NekoJSPaths.get().assets();
            DataGeneratorJS generator = new DataGeneratorJS(assets, "after_mods");
            PluginGenerationHooks.fireGenerateAssets(generator);
            ClientEvents.GENERATE_ASSETS.post(generator, "after_mods");
            // 语言条目按语言代码分别聚合，合并写入 lang/<lang>.json。
            for (String lang : ClientEvents.LANG.registeredKeys()) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langGenerator.writeTo(assets, lang);
            }
        } catch (Exception e) {
            NekoJS.LOGGER.debug("Client generation event failed", e);
        }
    }
}