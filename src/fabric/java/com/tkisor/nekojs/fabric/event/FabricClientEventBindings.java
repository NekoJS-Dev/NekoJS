package com.tkisor.nekojs.fabric.event;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableClientEvent;
import com.tkisor.nekojs.api.event.StableClientInputEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.client.ClientEventThread;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import com.tkisor.nekojs.wrapper.event.client.ClientCommandRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.client.ClientPlayerNetworkEventJS;
import com.tkisor.nekojs.wrapper.event.client.ClientTickEventJS;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Fabric client event bridge: tick events, Painter HUD/screen events, network/command/input/player
 * client events, setup stages, item tooltips, and the client script hook.
 *
 * <p>The HUD event uses Fabric's identified HUD layer API and the screen event uses
 * {@code ScreenEvents.afterExtract}; both run on the client render thread and pass the native
 * {@code GuiGraphicsExtractor} through the shared Painter wrapper.
 *
 * <p>Fabric loads CLIENT scripts at {@code CLIENT_STARTED}, after the initial resource reload.
 */
public final class FabricClientEventBindings {

    /** Shares the group name with bindings/event/client/ClientEvents (Fabric subset). */
    public static final EventGroup CLIENT_EVENTS = EventGroup.of("ClientEvents");

    public static final EventBusJS<ClientTickEventJS, Void> TICK_PRE =
            CLIENT_EVENTS.client("tickPre", ClientTickEventJS.class, api -> {
                api.versioned().when(e -> e == ClientTickEventJS.INSTANCE);
                api.stable().when(e -> e == ClientTickEventJS.STABLE_PHASE && ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> FabricClientStableEventProjections.stage("tick_pre"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    public static final EventBusJS<ClientTickEventJS, Void> TICK_POST =
            CLIENT_EVENTS.client("tickPost", ClientTickEventJS.class, api -> {
                api.versioned().when(e -> e == ClientTickEventJS.INSTANCE);
                api.stable().when(e -> e == ClientTickEventJS.STABLE_PHASE && ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> FabricClientStableEventProjections.stage("tick_post"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    @Deprecated
    public static final EventBusJS<ClientTickEventJS, Void> TICK =
            CLIENT_EVENTS.client("tick", ClientTickEventJS.class, api -> api.versioned());

    /** Per-frame HUD Painter payload, backed by Fabric's identified HUD layer API. */
    public static final EventBusJS<PainterJS, Void> HUD =
            CLIENT_EVENTS.client("hud", PainterJS.class, api -> api.versioned());

    /** Per-screen Painter payload, backed by Fabric ScreenEvents.afterExtract. */
    public static final EventBusJS<ScreenRenderEventJS, Void> SCREEN_RENDER =
            CLIENT_EVENTS.client("screenRender", ScreenRenderEventJS.class, api -> api.versioned());

    // ---- Fabric-API-native client events (published as both versioned and stable) ----

    public static final EventBusJS<ClientPlayerNetworkEventJS, Void> LOGGED_IN =
            CLIENT_EVENTS.client("loggedIn", ClientPlayerNetworkEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof ClientPlayerTransitionEvent));
                api.stable().when(event -> event instanceof ClientPlayerTransitionEvent)
                        .view(StablePlayerEvent.class, FabricClientStableEventProjections::clientNetwork)
                        .scriptTypes(ScriptType.CLIENT);
            });

    public static final EventBusJS<ClientPlayerNetworkEventJS, Void> LOGGED_OUT =
            CLIENT_EVENTS.client("loggedOut", ClientPlayerNetworkEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof ClientPlayerTransitionEvent));
                api.stable().when(event -> event instanceof ClientPlayerTransitionEvent)
                        .view(StablePlayerEvent.class, FabricClientStableEventProjections::clientNetwork)
                        .scriptTypes(ScriptType.CLIENT);
            });

    public static final EventBusJS<ClientCommandRegistryEventJS, Void> COMMAND_REGISTRY =
            CLIENT_EVENTS.client("commandRegistry", ClientCommandRegistryEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof ClientCommandTreeEvent));
                api.stable()
                        .when(event -> event instanceof ClientCommandTreeEvent && ClientEventThread.isCurrent())
                        .view(StableCommandRegistrationEvent.class, FabricClientStableEventProjections::clientCommandRegistry)
                        .scriptTypes(ScriptType.CLIENT);
            });

    // ---- Non-native client events (published as stable only) ----

    public static final EventBusJS<StablePlayerEvent, Void> PLAYER_TICK_PRE =
            CLIENT_EVENTS.client("playerTickPre", StablePlayerEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StablePlayerEvent, Void> PLAYER_TICK_POST =
            CLIENT_EVENTS.client("playerTickPost", StablePlayerEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StableClientInputEvent, Void> INTERACTION_KEY =
            CLIENT_EVENTS.add("interactionKey", ScriptType.CLIENT,
                    EventBusJS.of(StableClientInputEvent.class, true),
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StablePlayerEvent, Void> CLONED =
            CLIENT_EVENTS.client("cloned", StablePlayerEvent.class,
                    api -> api.stable().scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StableClientEvent, Void> REGISTER_KEY_MAPPINGS =
            CLIENT_EVENTS.client("registerKeyMappings", StableClientEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StableClientEvent, Void> REGISTER_MENU_SCREENS =
            CLIENT_EVENTS.client("registerMenuScreens", StableClientEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StableClientEvent, Void> REGISTER_RENDERERS =
            CLIENT_EVENTS.client("registerRenderers", StableClientEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    public static final EventBusJS<StableClientEvent, Void> REGISTER_PARTICLE_PROVIDERS =
            CLIENT_EVENTS.client("registerParticleProviders", StableClientEvent.class,
                    api -> api.stable()
                            .when(event -> ClientEventThread.isCurrent())
                            .scriptTypes(ScriptType.CLIENT));

    private static final Set<Event<ScreenEvents.AfterExtract>> SCREEN_HOOKS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Identifier HUD_ELEMENT_ID =
            Identifier.fromNamespaceAndPath("nekojs", "script_hud");

    private static Runnable initialClientLoad;

    private FabricClientEventBindings() {}

    private static void installScreenPainter(Event<ScreenEvents.AfterExtract> extracts) {
        synchronized (SCREEN_HOOKS) {
            if (SCREEN_HOOKS.add(extracts)) {
                extracts.register(FabricClientEventBindings::dispatchScreen);
            }
        }
    }

    /**
     * @param loadClientScripts loads the CLIENT scripts; runs once the client resources are ready,
     *                          at {@code CLIENT_STARTED}
     */
    public static void register(Runnable loadClientScripts) {
        initialClientLoad = loadClientScripts;
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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (LOGGED_IN.hasListeners()) {
                LOGGED_IN.post(new ClientPlayerNetworkEventJS(client.player));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (LOGGED_OUT.hasListeners()) {
                var player = client.player;
                client.execute(() -> LOGGED_OUT.post(new ClientPlayerNetworkEventJS(player)));
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            if (COMMAND_REGISTRY.hasListeners()) {
                COMMAND_REGISTRY.post(new ClientCommandRegistryEventJS(dispatcher, registryAccess));
            }
        });
        // Fabric's HUD layer API is the native 26.x equivalent of HUD rendering.
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR, HUD_ELEMENT_ID, FabricClientEventBindings::dispatchHud);
        // Flush CLIENT-side node timers on the client tick.
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                com.tkisor.nekojs.fabric.NekoJSFabricMod.flushClientNodeTimers());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                installScreenPainter(ScreenEvents.afterExtract(screen)));

        // Item tooltips (the twin of ItemEvents.TOOLTIP, dispatched by item id). Stage tooltips are
        // appended first so script tooltip listeners observe and can customize them.
        bridge.bindDispatched(
                com.tkisor.nekojs.bindings.event.ItemEvents.TOOLTIP,
                listener -> ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
                    StageGateEnforcer.appendClientItemStageTooltip(stack, lines);
                    listener.accept(
                            new com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS(stack, lines),
                            stack.getItem());
                }));
    }

    public static void postClientCommandTree(ClientPacketListener listener) {
        if (!COMMAND_REGISTRY.hasStableModuleListeners(ScriptType.CLIENT)) {
            return;
        }
        var dispatcher = ClientCommands.getActiveDispatcher();
        if (dispatcher != null) {
            var context = CommandBuildContext.simple(listener.registryAccess(), listener.enabledFeatures());
            COMMAND_REGISTRY.postStableModuleNotification(new ClientCommandTreeEvent(dispatcher, context), ScriptType.CLIENT);
        }
    }

    private static final class ClientPlayerTransitionEvent extends ClientPlayerNetworkEventJS {
        private ClientPlayerTransitionEvent(Player player) {
            super(player);
        }
    }

    private static final class ClientCommandTreeEvent extends ClientCommandRegistryEventJS {
        private ClientCommandTreeEvent(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                       CommandBuildContext registryAccess) {
            super(dispatcher, registryAccess);
        }
    }

    public static void postStableTickPre() {
        if (TICK_PRE.hasStableModuleListeners(ScriptType.CLIENT)) {
            TICK_PRE.postStableModuleNotification(ClientTickEventJS.STABLE_PHASE, ScriptType.CLIENT);
        }
    }

    public static void postStableTickPost() {
        if (TICK_POST.hasStableModuleListeners(ScriptType.CLIENT)) {
            TICK_POST.postStableModuleNotification(ClientTickEventJS.STABLE_PHASE, ScriptType.CLIENT);
        }
    }

    public static void postStableLogin(Player player) {
        if (player != null && LOGGED_IN.hasStableModuleListeners(ScriptType.CLIENT) && ClientEventThread.isCurrent()) {
            LOGGED_IN.postStableModuleNotification(new ClientPlayerTransitionEvent(player), ScriptType.CLIENT);
        }
    }

    public static void postStableLogout(Player player) {
        if (player != null && LOGGED_OUT.hasStableModuleListeners(ScriptType.CLIENT) && ClientEventThread.isCurrent()) {
            LOGGED_OUT.postStableModuleNotification(new ClientPlayerTransitionEvent(player), ScriptType.CLIENT);
        }
    }

    public static void beforeInitialOptions() {
        Runnable load = initialClientLoad;
        if (load == null) return;
        initialClientLoad = null;
        load.run();
    }

    public static void postInitialKeyMappings() {
        if (REGISTER_KEY_MAPPINGS.hasListeners()) {
            REGISTER_KEY_MAPPINGS.post(FabricClientStableEventProjections.stage("register_key_mappings"));
        }
    }

    public static void postInitialParticleProviders() {
        if (REGISTER_PARTICLE_PROVIDERS.hasListeners()) {
            REGISTER_PARTICLE_PROVIDERS.post(FabricClientStableEventProjections.stage("register_particle_providers"));
        }
    }

    public static void beforeInitialResourceReload() {
        if (REGISTER_MENU_SCREENS.hasListeners()) {
            REGISTER_MENU_SCREENS.post(FabricClientStableEventProjections.stage("register_menu_screens"));
        }
        if (REGISTER_RENDERERS.hasListeners()) {
            REGISTER_RENDERERS.post(FabricClientStableEventProjections.stage("register_renderers"));
        }
    }

    /** Hook entry for {@code MixinLocalPlayerTick}: {@code LocalPlayer#tick} HEAD. */
    public static void postPlayerTickPre(Player player) {
        if (PLAYER_TICK_PRE.hasListeners()) {
            PLAYER_TICK_PRE.post(FabricClientStableEventProjections.clientPlayer(player));
        }
    }

    /** Hook entry for {@code MixinLocalPlayerTick}: {@code LocalPlayer#tick} TAIL. */
    public static void postPlayerTickPost(Player player) {
        if (PLAYER_TICK_POST.hasListeners()) {
            PLAYER_TICK_POST.post(FabricClientStableEventProjections.clientPlayer(player));
        }
    }

    /** Posts after {@code ClientPacketListener#handleRespawn} installs the replacement player. */
    public static void postClientCloned(Player player, Player originalPlayer) {
        if (player != null && CLONED.hasListeners() && ClientEventThread.isCurrent()) {
            CLONED.post(FabricClientStableEventProjections.clientPlayer(player, originalPlayer));
        }
    }

    /**
     * Hook entry for {@code MixinMinecraftInteractionKey}: {@code Minecraft#startAttack},
     * {@code startUseItem}, and {@code pickBlock}.
     *
     * @return {@code true} if a script listener cancelled the interaction input
     */
    public static boolean postInteractionKey(
            boolean attack, boolean useItem, boolean pickBlock, InteractionHand hand) {
        if (!INTERACTION_KEY.hasListeners()) {
            return false;
        }
        return INTERACTION_KEY.post(FabricClientStableEventProjections.clientInteractionKey(
                attack, useItem, pickBlock, hand, Minecraft.getInstance().player));
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
