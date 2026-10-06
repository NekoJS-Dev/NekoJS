// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
//? if neoforge {
package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.client.renderer.NekoEntityRenderers;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
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

    public static void register(IEventBus modEventBus, NekoRuntimeRoot root) {
        modEventBus.addListener((FMLConstructModEvent event) -> onClientSetup(event, root));
        modEventBus.addListener((AddClientReloadListenersEvent event) -> onClientResourceReload(event, root));
        modEventBus.addListener(NekoJSClient::onRegisterEntityRenderers);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTickPost(event, root));
        NeoForge.EVENT_BUS.addListener(NekoJSClient::onLevelUnload);
        // Reload progress HUD (8e)：自包含订阅 RenderGuiEvent.Post，不走 ClientEvents
        com.tkisor.nekojs.client.hud.NekoReloadProgressHud.install();
        ClientEvents.bindModBus(modEventBus);
    }

    /// 某些事件需要极早期的时机，如RegisterKeyMappingsEvent
    private static void onClientSetup(FMLConstructModEvent event, NekoRuntimeRoot root) {
        event.enqueueWork(() -> {
            NekoJS.LOGGER.debug("Client environment ready, loading CLIENT scripts...");
            root.scriptManagerOf(ScriptType.CLIENT).loadScripts();
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.CLIENT).debug("Early script injection...");
            // 票 28：CLIENT 声明的初始 generation 收集点（脚本经 ClientEvents.postEffects
            // 声明 register/unregister）。初始 load 是非事务路径（没有候选/commit），这里
            // 对 active 总线收集一次，domain owner preflight 通过才安装完整 generation；
            // 不通过则整批拒绝、保持上一次 active 声明。后续 F3+T 资源 reload 走事务
            // reload 的 DOMAIN_PLAN 阶段（同一 owner，collect）。
            com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner postEffects = postEffectDomain(root);
            if (postEffects != null) {
                postEffects.applyInitialPlan();
            }
        });
    }

    /** 后处理声明域 owner（root 授权的 domain collector；未注册返回 null，收集点跳过）。 */
    private static com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner postEffectDomain(NekoRuntimeRoot root) {
        var collector = root == null ? null : root.domainCollector(com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner.DOMAIN);
        return collector instanceof com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner owner ? owner : null;
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityTypeBuilder.registeredEntityTypes().forEach(type -> event.registerEntityRenderer(type,
                NekoEntityRenderers.provider(EntityTypeBuilder.renderConfiguration(type))));
    }

    private static void onClientTickPost(ClientTickEvent.Post event, NekoRuntimeRoot root) {
        root.scriptManagerOf(ScriptType.CLIENT).flushReadyNodeTimers();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PDataSyncService.clearClientMirrors();
            // ClientData 键值存储随断线/切世界一并清空（服务端重连后会重新推送）
            ClientDataStore.SHARED.clear();
        }
    }

    private static void onClientResourceReload(AddClientReloadListenersEvent event, NekoRuntimeRoot root) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                root.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                // 旧环境的监听器已被 reload 清空、新环境没建起来时，玩家不会有任何提示——
                // 必须 error 级日志 + 错误面板（rt/ 条目），不再只打 DEBUG
                NekoJS.LOGGER.error("CLIENT script reload (F3+T) failed", e);
                root.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", e);
            }
            postClientGeneration(root);
        });
    }

    /**
     * 客户端生成事件：脚本把 asset JSON 写入 {@code <gameDir>/nekojs/assets}（磁盘 resource
     * pack，懒读保证 reload 时序正确）。先聚合 lang 再生成 assets，与 KubeJS 流程一致。
     */
    private static void postClientGeneration(NekoRuntimeRoot root) {
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
            // 本轮语言集合 = 插件 generatedLangs() 声明 ∪ 脚本 keyed listener 语言，字典序确定；
            // 先整体校验再逐语言生成，任一非法语言代码在写入任何 lang 文件之前整批拒绝，
            // 因此不会产生部分语言文件（非 en_us 的插件语言必须显式声明，见 generatedLangs()）。
            var langs = PluginGenerationHooks.resolveGeneratedLangs(ClientEvents.LANG.registeredKeys());
            for (String lang : langs) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langGenerator.writeTo(assets, lang);
            }
        } catch (Exception e) {
            // 资产生成失败 = 客户端脚本产物不完整（模型/lang 缺失），同样进错误面板
            NekoJS.LOGGER.error("Client asset generation failed", e);
            root.errorTracker().recordCallbackError(ScriptType.CLIENT, "generate_assets", e);
        }
    }
}
//?}
