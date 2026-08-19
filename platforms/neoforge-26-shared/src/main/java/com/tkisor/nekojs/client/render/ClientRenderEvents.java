package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
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
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        dispatchWorld(ClientRenderRegistry.WorldLayer.EARLY, event);
    }

    @SubscribeEvent
    public static void onAfterWeather(RenderLevelStageEvent.AfterWeather event) {
        dispatchWorld(ClientRenderRegistry.WorldLayer.NORMAL, event);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        dispatchWorld(ClientRenderRegistry.WorldLayer.LATE, event);
    }

    private static void dispatchWorld(ClientRenderRegistry.WorldLayer layer, RenderLevelStageEvent event) {
        if (!ClientRenderRegistry.hasWorld(layer)) {
            return;
        }
        var cameraState = event.getLevelRenderState().cameraRenderState;
        WorldRenderContextJS ctx = new WorldRenderContextJS(
                cameraState != null ? cameraState.pos : net.minecraft.world.phys.Vec3.ZERO,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        ClientRenderRegistry.dispatchWorld(layer, ctx);
    }

    private static float partialTickOf(RenderGuiEvent event) {
        return event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }
}
