// 26.x implementation, so this file must not contain version guards. The 1.21.1 implementation
// is the same-named file under versions/1.21.1/src; changing this file's behavior means
// updating that one too.
//? if neoforge {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Dispatch hooks for script renderers (26.x): RenderGuiEvent.Pre/Post drives the HUD layers and
 * the phased RenderLevelStageEvent subclasses drive the world layers. Each frame first checks
 * hasListeners as a fast path, so no registered renderer means no allocation.
 *
 * <p>Layer mapping: HUD {@code background} renders on RenderGuiEvent.Pre (before the vanilla
 * HUD); {@code normal}/{@code foreground} render on RenderGuiEvent.Post (after it, ordered by
 * layer then priority). World {@code early} maps to AfterTranslucentBlocks, {@code normal} to
 * AfterWeather, {@code late} to AfterLevel. 26.x has no getCamera()/getPartialTick(): the camera
 * position comes from {@code LevelRenderState.cameraRenderState} and partialTick from
 * {@code Minecraft#getDeltaTracker}.
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
        // ctx is immutable: the normal and foreground layers reuse the same instance
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
//?}
