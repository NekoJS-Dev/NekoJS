//? if neoforge {
package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableClientEvent;
import com.tkisor.nekojs.api.event.StableClientInputEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.client.ClientEventThread;
import com.tkisor.nekojs.client.posteffect.PostEffectEventJS;
import com.tkisor.nekojs.client.render.RenderRegistrationBusJS;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import net.minecraft.client.Minecraft;
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

/**
 * Client event group for {@code client_scripts}: tick, login and logout, registration events
 * (key bindings, screens, renderers, particles), plus HUD and screen drawing.
 */
@Doc("Client event group for client scripts: tick, login/logout, registrations, assets/lang, post-effects, and HUD/world rendering.")
public interface ClientEvents {
    EventGroup GROUP = EventGroup.of("ClientEvents");

    /** Fired at the start of each client tick. */
    @Doc("Fired at the start of each client tick.")
    EventBusJS<ClientTickEvent.Pre, Void> TICK_PRE =
            GROUP.client("tickPre", ClientTickEvent.Pre.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent() && Minecraft.getInstance().isGameLoadFinished())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("tick_pre"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired at the end of each client tick. */
    @Doc("Fired at the end of each client tick.")
    EventBusJS<ClientTickEvent.Post, Void> TICK_POST =
            GROUP.client("tickPost", ClientTickEvent.Post.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent() && Minecraft.getInstance().isGameLoadFinished())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("tick_post"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /**
     * Legacy alias for {@link #TICK_POST}. Scripts should migrate to {@code tickPre} or {@code tickPost}.
     */
    @Doc("Deprecated alias for tickPost. Prefer tickPre or tickPost.")
    @Deprecated
    EventBusJS<ClientTickEvent.Post, Void> TICK =
            GROUP.client("tick", ClientTickEvent.Post.class, api -> api.versioned());

    /**
     * Player tick (pre), observed on the client: posts the raw NeoForge
     * {@link PlayerTickEvent.Pre} whose entity is the local player (typed as the common
     * {@code Player}, so the shape matches the server-side bus). Fires on the client
     * thread, only in {@code client_scripts}; the server-side counterpart is
     * {@code PlayerEvents.tickPre}. The native event fires on both logical sides —
     * this bus is bound through a side filter and only dispatches client instances.
     */
    @Doc("Fired at the start of each local player tick on the client thread.")
    EventBusJS<PlayerTickEvent.Pre, Void> PLAYER_TICK_PRE =
            GROUP.client("playerTickPre", PlayerTickEvent.Pre.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StablePlayerEvent.class, e -> NeoForgeClientStableEventProjections.clientPlayer(e.getEntity()))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /**
     * Player tick (post), observed on the client: posts the raw NeoForge
     * {@link PlayerTickEvent.Post}. Fires on the client thread, only in {@code client_scripts};
     * the server-side counterpart is {@code PlayerEvents.tickPost}. Same side filter as
     * {@link #PLAYER_TICK_PRE}: logical-server instances are never dispatched here.
     */
    @Doc("Fired at the end of each local player tick on the client thread.")
    EventBusJS<PlayerTickEvent.Post, Void> PLAYER_TICK_POST =
            GROUP.client("playerTickPost", PlayerTickEvent.Post.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StablePlayerEvent.class, e -> NeoForgeClientStableEventProjections.clientPlayer(e.getEntity()))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /**
     * Interaction key input (attack / use item / pick block), client-side only: posts
     * the raw NeoForge {@link InputEvent.InteractionKeyMappingTriggered}. Fires on the
     * client thread when the player presses the attack / use / pick key, only in
     * {@code client_scripts}. Cancellable: a listener returning {@code true} blocks vanilla
     * processing of the key. Use {@code isAttack()} / {@code isUseItem()} /
     * {@code isPickBlock()} to tell them apart; use-item inputs fire once per hand.
     */
    @Doc("Fired on the client when attack, use-item, or pick-block key input triggers; return true to cancel.")
    EventBusJS<InputEvent.InteractionKeyMappingTriggered, Void> INTERACTION_KEY =
            GROUP.client("interactionKey", InputEvent.InteractionKeyMappingTriggered.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StableClientInputEvent.class, NeoForgeClientStableEventProjections::clientInteractionKey)
                        .cancellable()
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired when the local client player logs in to a server or singleplayer world. */
    @Doc("Fired when the local client player logs in.")
    EventBusJS<ClientPlayerNetworkEvent.LoggingIn, Void> LOGGED_IN =
            GROUP.client("loggedIn", ClientPlayerNetworkEvent.LoggingIn.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getPlayer() != null && ClientEventThread.isCurrent())
                        .view(StablePlayerEvent.class, NeoForgeClientStableEventProjections::clientLogin)
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired when the local client player logs out. */
    @Doc("Fired when the local client player logs out.")
    EventBusJS<ClientPlayerNetworkEvent.LoggingOut, Void> LOGGED_OUT =
            GROUP.client("loggedOut", ClientPlayerNetworkEvent.LoggingOut.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getPlayer() != null && ClientEventThread.isCurrent())
                        .view(StablePlayerEvent.class, NeoForgeClientStableEventProjections::clientLogout)
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired when the local client player entity is cloned (e.g., on respawn). */
    @Doc("Fired when the local client player entity is cloned.")
    EventBusJS<ClientPlayerNetworkEvent.Clone, Void> CLONED =
            GROUP.client("cloned", ClientPlayerNetworkEvent.Clone.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getNewPlayer() != null && ClientEventThread.isCurrent())
                        .view(StablePlayerEvent.class, NeoForgeClientStableEventProjections::clientClone)
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired to register client-side commands. */
    @Doc("Fired to register client-side commands.")
    EventBusJS<RegisterClientCommandsEvent, Void> COMMAND_REGISTRY =
            GROUP.client("commandRegistry", RegisterClientCommandsEvent.class, api -> {
                api.versioned().when(NeoForgeClientCommandRouting::isVersioned);
                api.stable()
                        .when(NeoForgeClientCommandRouting::isStable)
                        .view(StableCommandRegistrationEvent.class, NeoForgeClientStableEventProjections::clientCommandRegistry)
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired on the mod bus to register key mappings. */
    @Doc("Fired to register vanilla key mappings.")
    EventBusJS<RegisterKeyMappingsEvent, Void> REGISTER_KEY_MAPPINGS =
            GROUP.client("registerKeyMappings", RegisterKeyMappingsEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("register_key_mappings"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired on the mod bus to register menu screens. */
    @Doc("Fired to register container menu screens.")
    EventBusJS<RegisterMenuScreensEvent, Void> REGISTER_MENU_SCREENS =
            GROUP.client("registerMenuScreens", RegisterMenuScreensEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("register_menu_screens"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /** Fired on the mod bus to register entity and block entity renderers. */
    @Doc("Fired to register entity and block entity renderers.")
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_RENDERERS =
            GROUP.client("registerRenderers", EntityRenderersEvent.RegisterRenderers.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("register_renderers"))
                        .scriptTypes(ScriptType.CLIENT);
            });

    /**
     * Legacy alias for {@link #REGISTER_RENDERERS}. Entities and block entities share one renderer
     * registration bus; scripts should migrate to {@code registerRenderers}.
     */
    @Doc("Deprecated alias for registerRenderers.")
    @Deprecated
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_ENTITY_RENDERERS =
            GROUP.client("registerEntityRenderers", EntityRenderersEvent.RegisterRenderers.class, api -> api.versioned());

    /**
     * Legacy alias for {@link #REGISTER_RENDERERS}. Entities and block entities share one renderer
     * registration bus; scripts should migrate to {@code registerRenderers}.
     */
    @Doc("Deprecated alias for registerRenderers.")
    @Deprecated
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_BLOCK_ENTITY_RENDERERS =
            GROUP.client("registerBlockEntityRenderers", EntityRenderersEvent.RegisterRenderers.class, api -> api.versioned());

    /** Fired on the mod bus to register particle providers. */
    @Doc("Fired to register particle providers.")
    EventBusJS<RegisterParticleProvidersEvent, Void> REGISTER_PARTICLE_PROVIDERS =
            GROUP.client("registerParticleProviders", RegisterParticleProvidersEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ClientEventThread.isCurrent())
                        .view(StableClientEvent.class, e -> NeoForgeClientStableEventProjections.stage("register_particle_providers"))
                        .scriptTypes(ScriptType.CLIENT);
            });

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

    /** Asset generation event: scripts write resource pack JSON (models, blockstates, and so on). */
    @Doc("Asset generation event for writing virtual resource pack JSON (models, blockstates, etc.). Dispatches by stage.")
    EventBusJS<DataGeneratorJS, String> GENERATE_ASSETS =
            GROUP.client("generateAssets", DataGeneratorJS.class, ASSET_STAGE_KEY, api -> api.versioned());

    /**
     * Language generation event: scripts collect translation entries per language code
     * ({@code en_us} and so on).
     */
    @Doc("Language generation event for adding translation entries per locale (e.g. 'en_us').")
    EventBusJS<LangGeneratorJS, String> LANG =
            GROUP.client("lang", LangGeneratorJS.class, LANG_KEY, api -> api.versioned());

    /**
     * Post-processing declaration event: scripts declare post-processing chains ({@code register}
     * / {@code unregister}) to be installed on client reload. Runtime changes use the
     * {@code PostEffects} bindings ({@code set}, {@code clear}, {@code toggle}, {@code current})
     * instead of this event.
     */
    @Doc("Post-processing declaration event for registering or unregistering shader chains on client reload.")
    EventBusJS<PostEffectEventJS, Void> POST_EFFECTS =
            GROUP.client("postEffects", PostEffectEventJS.class, api -> api.versioned());

    /** HUD drawing event (after the GUI render each frame); the argument is a {@link PainterJS}. */
    @Doc("HUD drawing event fired after GUI rendering each frame with a PainterJS.")
    EventBusJS<PainterJS, Void> HUD = GROUP.client("hud", PainterJS.class, api -> api.versioned());

    /** Screen drawing event (after a Screen renders); the argument is a {@link ScreenRenderEventJS}. */
    @Doc("Screen drawing event fired after a Screen renders with a ScreenRenderEventJS.")
    EventBusJS<ScreenRenderEventJS, Void> SCREEN_RENDER = GROUP.client("screenRender", ScreenRenderEventJS.class, api -> api.versioned());

    /**
     * Script HUD renderer registration entry (calling it registers a resident renderer tracked by
     * id rather than a regular listener). Form:
     * {@code hudRender('my_hud', { layer: 'normal', priority: 0 }, (ctx, gui) => {})}, where
     * {@code layer} is {@code background|normal|foreground} (background draws below the vanilla
     * HUD) and smaller priority draws first within a layer. {@code ctx} is a
     * {@code HudRenderContextJS} and {@code gui} is the raw {@code GuiGraphicsExtractor}.
     * Redeclaring the same id replaces the previous renderer, and a client script reload
     * atomically swaps the active set (falling back to the previous set if reload fails).
     */
    @Doc("Registers a resident HUD renderer by id: hudRender(id, options, (ctx, gui) => {}).")
    EventBusJS<Object, Void> HUD_RENDER =
            GROUP.add("hudRender", ScriptType.CLIENT, RenderRegistrationBusJS.hud(), api -> api.versioned());

    /**
     * Script world renderer registration entry (same registration model as {@link #HUD_RENDER}).
     * Form: {@code worldRender('my_lines', { layer: 'normal', priority: 0 }, ctx => {})}, where
     * {@code layer} is {@code early|normal|late}. {@code ctx} is a {@code WorldRenderContextJS}
     * providing 3D drawing helpers in world coordinates.
     */
    @Doc("Registers a resident 3D world renderer by id: worldRender(id, options, ctx => {}).")
    EventBusJS<Object, Void> WORLD_RENDER =
            GROUP.add("worldRender", ScriptType.CLIENT, RenderRegistrationBusJS.world(), api -> api.versioned());

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
