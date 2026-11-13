// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.NekoJS;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Dispatch hooks for script renderers (26.x): RenderGuiEvent.Pre/Post drive the HUD layers and
 * the phase-specific RenderLevelStageEvent subclasses drive the world layers. Every frame
 * starts with a hasListeners fast-path check, so a frame with no registered renderers allocates
 * nothing.
 *
 * <p>Layer mapping: HUD {@code background} goes to RenderGuiEvent.Pre (before the vanilla
 * HUD); {@code normal}/{@code foreground} go to RenderGuiEvent.Post (after it, in layer then
 * priority order). In the world, {@code early} goes to AfterTranslucentBlocks, {@code normal}
 * to AfterWeather and {@code late} to AfterLevel. 26.x has no getCamera()/getPartialTick():
 * the camera position comes from {@code LevelRenderState.cameraRenderState} and partialTick
 * from {@code Minecraft#getDeltaTracker}.
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
        // ctx is immutable: the normal and foreground layers share one instance
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
