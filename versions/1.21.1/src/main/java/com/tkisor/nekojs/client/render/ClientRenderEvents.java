// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.NekoJS;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 脚本渲染器分发钩子（26.x）：RenderGuiEvent.Pre/Post 驱动 HUD 层，分阶段
 * RenderLevelStageEvent 子类驱动世界层。每帧先做 hasListeners 快路径检查，
 * 无注册渲染器时零分配。
 *
 * <p>层映射：HUD {@code background} → RenderGuiEvent.Pre（原版 HUD 之前）；
 * {@code normal}/{@code foreground} → RenderGuiEvent.Post（之后，按层+优先级顺序）。
 * 世界 {@code early} → AfterTranslucentBlocks，{@code normal} → AfterWeather，
 * {@code late} → AfterLevel。26.x 无 getCamera()/getPartialTick()，相机位置取
 * {@code LevelRenderState.cameraRenderState}，partialTick 取 {@code Minecraft#getDeltaTracker}。
 */
@EventBusSubscriber(modid = NekoJS.MODID, value = Dist.CLIENT)
public class ClientRenderEvents {

    @SubscribeEvent
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        if (!ClientRenderRegistry.hasHud(ClientRenderRegistry.HudLayer.BACKGROUND)) {
            return;
        }
        ClientRenderRegistry.dispatchHud(
                ClientRenderRegistry.HudLayer.BACKGROUND,
                new HudRenderContextJS(event.getGuiGraphics(), partialTickOf(event)),
                event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        boolean normal = ClientRenderRegistry.hasHud(ClientRenderRegistry.HudLayer.NORMAL);
        boolean foreground = ClientRenderRegistry.hasHud(ClientRenderRegistry.HudLayer.FOREGROUND);
        if (!normal && !foreground) {
            return;
        }
        // ctx 不可变：normal 与 foreground 层复用同一实例
        HudRenderContextJS ctx = new HudRenderContextJS(event.getGuiGraphics(), partialTickOf(event));
        if (normal) {
            ClientRenderRegistry.dispatchHud(ClientRenderRegistry.HudLayer.NORMAL, ctx, event.getGuiGraphics());
        }
        if (foreground) {
            ClientRenderRegistry.dispatchHud(ClientRenderRegistry.HudLayer.FOREGROUND, ctx, event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onLevelRender(RenderLevelStageEvent event) {
        ClientRenderRegistry.WorldLayer layer;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            layer = ClientRenderRegistry.WorldLayer.EARLY;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            layer = ClientRenderRegistry.WorldLayer.NORMAL;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            layer = ClientRenderRegistry.WorldLayer.LATE;
        } else {
            return;
        }
        if (!ClientRenderRegistry.hasWorld(layer)) {
            return;
        }
        WorldRenderContextJS ctx = new WorldRenderContextJS(
                event.getCamera().getPosition(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        ClientRenderRegistry.dispatchWorld(layer, ctx);
    }

    private static float partialTickOf(RenderGuiEvent event) {
        return event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }
}
