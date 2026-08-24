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
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
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
        // Reload progress HUD (8e)：自包含订阅 RenderGuiEvent.Post，不走 ClientEvents
        com.tkisor.nekojs.client.hud.NekoReloadProgressHud.install();
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
            // ClientData 键值存储随断线/切世界一并清空（服务端重连后会重新推送）
            ClientDataStore.SHARED.clear();
        }
    }

    private static void onClientResourceReload(AddClientReloadListenersEvent event) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                // 旧环境的监听器已被 reload 清空、新环境没建起来时，玩家不会有任何提示——
                // 必须 error 级日志 + 错误面板（rt/ 条目），不再只打 DEBUG
                NekoJS.LOGGER.error("CLIENT script reload (F3+T) failed", e);
                NekoJSMod.RUNTIME_ROOT.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", e);
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
            // 脚本模型文件已落盘后，为声明过 renderType 且未自写模型的方块补默认模型
            // （26.x 模型驱动：translucent 需要 force_translucent 贴图引用）
            BlockModelGenerator.generateDefaultModels(generator);
            // 请求过 spawnEgg() 的实体：补默认蛋模型（26.x 无运行时染色，纹理数据驱动）
            BlockModelGenerator.generateSpawnEggModels(generator);
            // 语言条目按语言代码分别聚合，合并写入 lang/<lang>.json。
            for (String lang : ClientEvents.LANG.registeredKeys()) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langGenerator.writeTo(assets, lang);
            }
        } catch (Exception e) {
            // 资产生成失败 = 客户端脚本产物不完整（模型/lang 缺失），同样进错误面板
            NekoJS.LOGGER.error("Client asset generation failed", e);
            NekoJSMod.RUNTIME_ROOT.errorTracker().recordCallbackError(ScriptType.CLIENT, "generate_assets", e);
        }
    }
}