//? if neoforge {
package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.client.posteffect.PostEffectEventJS;
import com.tkisor.nekojs.client.render.RenderRegistrationBusJS;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Client event group (client scripts): tick, login and logout, registration events (key bindings,
 * screens, renderers, particles), plus HUD and screen drawing. */
public interface ClientEvents {
    EventGroup GROUP = EventGroup.of("ClientEvents");

    EventBusJS<ClientTickEvent.Pre, Void> TICK_PRE =
            GROUP.client("tickPre", ClientTickEvent.Pre.class);
    EventBusJS<ClientTickEvent.Post, Void> TICK_POST =
            GROUP.client("tickPost", ClientTickEvent.Post.class);
    // tick: the bare-name alias of tickPost. Scripts should move to tickPre/tickPost.
    @Deprecated
    EventBusJS<ClientTickEvent.Post, Void> TICK =
            GROUP.client("tick", ClientTickEvent.Post.class);
    /**
     * Player tick (pre), observed on the client: posts the raw NeoForge
     * {@link PlayerTickEvent.Pre} whose entity is the local player (typed as the common
     * {@code Player}, so the shape matches the server-side bus). Fires on the client
     * thread, only in client_scripts; the server-side counterpart is
     * {@code PlayerEvents.tickPre}. The native event fires on BOTH logical sides —
     * this bus is bound through a side filter and only dispatches client instances.
     */
    EventBusJS<PlayerTickEvent.Pre, Void> PLAYER_TICK_PRE =
            GROUP.client("playerTickPre", PlayerTickEvent.Pre.class);
    /**
     * Player tick (post), observed on the client: posts the raw NeoForge
     * {@link PlayerTickEvent.Post}. Fires on the client thread, only in client_scripts;
     * the server-side counterpart is {@code PlayerEvents.tickPost}. Same side filter as
     * {@link #PLAYER_TICK_PRE}: logical-server instances are never dispatched here.
     */
    EventBusJS<PlayerTickEvent.Post, Void> PLAYER_TICK_POST =
            GROUP.client("playerTickPost", PlayerTickEvent.Post.class);
    /**
     * Interaction key input (attack / use item / pick block), client-side only: posts
     * the raw NeoForge {@link InputEvent.InteractionKeyMappingTriggered}. Fires on the
     * client thread when the player presses the attack / use / pick key, only in
     * client_scripts. Cancellable: a listener returning {@code true} blocks vanilla
     * processing of the key. Use {@code isAttack()} / {@code isUseItem()} /
     * {@code isPickBlock()} to tell them apart; use-item inputs fire once per hand.
     */
    EventBusJS<InputEvent.InteractionKeyMappingTriggered, Void> INTERACTION_KEY =
            GROUP.client("interactionKey", InputEvent.InteractionKeyMappingTriggered.class);
    EventBusJS<ClientPlayerNetworkEvent.LoggingIn, Void> LOGGED_IN =
            GROUP.client("loggedIn", ClientPlayerNetworkEvent.LoggingIn.class);
    EventBusJS<ClientPlayerNetworkEvent.LoggingOut, Void> LOGGED_OUT =
            GROUP.client("loggedOut", ClientPlayerNetworkEvent.LoggingOut.class);
    EventBusJS<ClientPlayerNetworkEvent.Clone, Void> CLONED =
            GROUP.client("cloned", ClientPlayerNetworkEvent.Clone.class);
    EventBusJS<RegisterClientCommandsEvent, Void> COMMAND_REGISTRY =
            GROUP.client("commandRegistry", RegisterClientCommandsEvent.class);
    EventBusJS<RegisterKeyMappingsEvent, Void> REGISTER_KEY_MAPPINGS =
            GROUP.client("registerKeyMappings", RegisterKeyMappingsEvent.class);
    EventBusJS<RegisterMenuScreensEvent, Void> REGISTER_MENU_SCREENS =
            GROUP.client("registerMenuScreens", RegisterMenuScreensEvent.class);
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_RENDERERS =
            GROUP.client("registerRenderers", EntityRenderersEvent.RegisterRenderers.class);
    // Entities and block entities share one renderer registration bus; the two names below are
    // legacy aliases. Scripts should move to registerRenderers.
    @Deprecated
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_ENTITY_RENDERERS =
            GROUP.client("registerEntityRenderers", EntityRenderersEvent.RegisterRenderers.class);
    @Deprecated
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_BLOCK_ENTITY_RENDERERS =
            GROUP.client("registerBlockEntityRenderers", EntityRenderersEvent.RegisterRenderers.class);
    EventBusJS<RegisterParticleProvidersEvent, Void> REGISTER_PARTICLE_PROVIDERS =
            GROUP.client("registerParticleProviders", RegisterParticleProvidersEvent.class);

    DispatchKey<DataGeneratorJS, String> ASSET_STAGE_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(DataGeneratorJS event) {
            return event.getStage();
        }
    };

    DispatchKey<LangGeneratorJS, String> LANG_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(LangGeneratorJS event) {
            return event.getLang();
        }
    };

    /** Asset generation event: scripts write resource pack JSON (models, blockstates and so on). */
    EventBusJS<DataGeneratorJS, String> GENERATE_ASSETS =
            GROUP.client("generateAssets", DataGeneratorJS.class, ASSET_STAGE_KEY);

    /** Language generation event: scripts collect translation entries per language code
     * ({@code en_us} and so on). */
    EventBusJS<LangGeneratorJS, String> LANG =
            GROUP.client("lang", LangGeneratorJS.class, LANG_KEY);

    /**
     * Post-processing declaration event: scripts declare the post-processing chain for the next
     * client generation ({@code register}/{@code unregister}). Declarations are collected inert
     * during the candidate phase; at the commit point the platform adapter installs the new
     * generation as a whole and releases the listeners and resources of the old generation.
     * Runtime changes go through the {@code PostEffects.set/clear/toggle/current} bindings, not
     * this event. This is the existing ClientEvents client resource/reload sub-event, not a
     * second PostEffects event.
     */
    EventBusJS<PostEffectEventJS, Void> POST_EFFECTS =
            GROUP.client("postEffects", PostEffectEventJS.class);

    /** HUD drawing event (after the GUI render each frame); the argument is a {@link PainterJS}. */
    EventBusJS<PainterJS, Void> HUD = GROUP.client("hud", PainterJS.class);

    /** Screen drawing event (after a Screen renders); the argument is a {@link ScreenRenderEventJS}. */
    EventBusJS<ScreenRenderEventJS, Void> SCREEN_RENDER = GROUP.client("screenRender", ScreenRenderEventJS.class);

    /**
     * Script HUD renderer registration entry (not a regular bus: calling it registers a resident
     * renderer tracked by id). Form:
     * {@code hudRender('my_hud', { layer: 'normal', priority: 0 }, (ctx, gui) => {})}, where the
     * layer is {@code background|normal|foreground} (background draws below the vanilla HUD). Within
     * one layer a smaller priority draws first. The callback's ctx is a {@code HudRenderContextJS}
     * (drawing helpers for partialTick, screen size, text, rect, texture and so on) and gui is the
     * raw GuiGraphicsExtractor. Redeclaring the same id inside one generation replaces the old
     * renderer. A CLIENT reload swaps the whole batch at the commit point (ids no longer declared
     * retire), and when the candidate fails the previous generation's renderers keep serving.
     */
    EventBusJS<Object, Void> HUD_RENDER =
            GROUP.add("hudRender", ScriptType.CLIENT, RenderRegistrationBusJS.hud());

    /**
     * Script world renderer registration entry (not a regular bus, same as {@link #HUD_RENDER}).
     * Form: {@code worldRender('my_lines', { layer: 'normal', priority: 0 }, ctx => {})}, where the
     * layer is {@code early|normal|late}. The callback's ctx is a
     * {@code WorldRenderContextJS} (3D drawing helpers for camera position, partialTick, line, box
     * and so on, in world coordinates).
     */
    EventBusJS<Object, Void> WORLD_RENDER =
            GROUP.add("worldRender", ScriptType.CLIENT, RenderRegistrationBusJS.world());

    EventBusForgeBridge MAIN_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(TICK_PRE)
            .bind(TICK_POST)
            .bind(TICK)
            // PlayerTickEvent fires on BOTH logical sides (client instances arrive on the
            // Render thread): the CLIENT buses below only dispatch client instances —
            // mirror of the server-side filter in PlayerEvents.FORGE_BRIDGE
            .bind(PLAYER_TICK_PRE, e -> e.getEntity().level().isClientSide())
            .bind(PLAYER_TICK_POST, e -> e.getEntity().level().isClientSide())
            // InteractionKeyMappingTriggered only ever fires on the logical client
            .bind(INTERACTION_KEY)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CLONED)
            .bind(COMMAND_REGISTRY)
            .bindTransformed(HUD, event -> new PainterJS(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false)), RenderGuiEvent.Post.class)
            .bindTransformed(SCREEN_RENDER, event -> new ScreenRenderEventJS(
                    new PainterJS(event.getGuiGraphics(), event.getPartialTick()),
                    event.getScreen(), event.getMouseX(), event.getMouseY()), ScreenEvent.Render.Post.class);

    static void bindModBus(IEventBus modEventBus) {
        EventBusForgeBridge.create(modEventBus)
                .bind(REGISTER_KEY_MAPPINGS)
                .bind(REGISTER_MENU_SCREENS)
                .bind(REGISTER_RENDERERS)
                .bind(REGISTER_ENTITY_RENDERERS)
                .bind(REGISTER_BLOCK_ENTITY_RENDERERS)
                .bind(REGISTER_PARTICLE_PROVIDERS);
    }
}
//?}
