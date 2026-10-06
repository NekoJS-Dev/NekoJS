package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import com.tkisor.nekojs.wrapper.event.client.ClientTickEventJS;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.event.Event;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Fabric client event bridge: tick events, Painter HUD/screen events, and the client script hook.
 *
 * <p>The HUD event uses Fabric's identified HUD layer API and the screen event uses
 * {@code ScreenEvents.afterExtract}; both run on the client render thread and pass the native
 * {@code GuiGraphicsExtractor} through the shared Painter wrapper. The identified
 * {@code hudRender}/{@code worldRender} registration surface remains a separate NeoForge-only
 * capability on Fabric.
 *
 * <p>Fabric loads CLIENT scripts at {@code CLIENT_STARTED}, after the initial resource reload.
 */
public final class FabricClientEventBindings {

    /** 与 NeoForge 侧 bindings/event/client/ClientEvents 同名（fabric 子集）。 */
    public static final EventGroup CLIENT_EVENTS = EventGroup.of("ClientEvents");

    public static final EventBusJS<ClientTickEventJS, Void> TICK_PRE =
            CLIENT_EVENTS.client("tickPre", ClientTickEventJS.class);

    public static final EventBusJS<ClientTickEventJS, Void> TICK_POST =
            CLIENT_EVENTS.client("tickPost", ClientTickEventJS.class);

    @Deprecated
    public static final EventBusJS<ClientTickEventJS, Void> TICK =
            CLIENT_EVENTS.client("tick", ClientTickEventJS.class);

    /** Per-frame HUD Painter payload, backed by Fabric's identified HUD layer API. */
    public static final EventBusJS<PainterJS, Void> HUD =
            CLIENT_EVENTS.client("hud", PainterJS.class);

    /** Per-screen Painter payload, backed by Fabric ScreenEvents.afterExtract. */
    public static final EventBusJS<ScreenRenderEventJS, Void> SCREEN_RENDER =
            CLIENT_EVENTS.client("screenRender", ScreenRenderEventJS.class);

    private static final Set<Event<ScreenEvents.AfterExtract>> SCREEN_HOOKS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Identifier HUD_ELEMENT_ID =
            Identifier.fromNamespaceAndPath("nekojs", "script_hud");

    private static void installScreenPainter(Event<ScreenEvents.AfterExtract> extracts) {
        synchronized (SCREEN_HOOKS) {
            if (SCREEN_HOOKS.add(extracts)) {
                extracts.register(FabricClientEventBindings::dispatchScreen);
            }
        }
    }

    /**
     * @param loadClientScripts CLIENT 脚本加载动作（客户端资源就绪后、CLIENT_STARTED 时机）
     */
    public static void register(Runnable loadClientScripts) {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> loadClientScripts.run());
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        bridge.bind(TICK_PRE,
                listener -> ClientTickEvents.START_CLIENT_TICK.register(client ->
                        listener.accept(ClientTickEventJS.INSTANCE)));
        bridge.bind(TICK_POST,
                listener -> ClientTickEvents.END_CLIENT_TICK.register(client ->
                        listener.accept(ClientTickEventJS.INSTANCE)));
        bridge.bind(TICK,
                listener -> ClientTickEvents.END_CLIENT_TICK.register(client ->
                        listener.accept(ClientTickEventJS.INSTANCE)));
        // Fabric's HUD layer API is the native 26.x equivalent of RenderGuiEvent.
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR, HUD_ELEMENT_ID, FabricClientEventBindings::dispatchHud);
        // 与 NeoForge 侧 NekoJSClient#onClientTickPost 同：tick 上冲刷 CLIENT 侧 node timers
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                com.tkisor.nekojs.fabric.NekoJSFabricMod.flushClientNodeTimers());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                installScreenPainter(ScreenEvents.afterExtract(screen)));

        // 物品 tooltip（孪生 ItemEvents.TOOLTIP，按物品 id dispatch）：lines 是渲染前的
        // 可变列表，监听器 mutate 即生效（fabric 回调不可取消整段渲染，删空列表即近似取消）
        bridge.bindDispatched(
                com.tkisor.nekojs.bindings.event.ItemEvents.TOOLTIP,
                listener -> ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) ->
                        listener.accept(
                                new com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS(stack, lines),
                                stack.getItem())));
    }

    private static void dispatchHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (HUD.hasListeners()) {
            HUD.post(new PainterJS(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        }
    }

    private static void dispatchScreen(Screen screen, GuiGraphicsExtractor graphics,
                                       int mouseX, int mouseY, float tickProgress) {
        if (SCREEN_RENDER.hasListeners()) {
            SCREEN_RENDER.post(new ScreenRenderEventJS(
                    new PainterJS(graphics, tickProgress), screen, mouseX, mouseY));
        }
    }
}
