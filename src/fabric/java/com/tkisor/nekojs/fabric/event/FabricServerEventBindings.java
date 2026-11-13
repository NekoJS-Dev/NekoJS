package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.wrapper.event.player.PlayerCloneEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerRespawnEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerTickEventJS;
import com.tkisor.nekojs.wrapper.event.server.DatapackSyncEventJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableLoadEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;
import com.tkisor.nekojs.wrapper.event.server.TagUpdatedEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Provide Fabric bridges for server events: lifecycle (aboutToStart, starting, started, stopping, stopped),
 * server ticks (tickPre, tickPost), and player connection events (loggedIn, loggedOut).
 *
 * <p>The group and bus names are consistent with NeoForge. Since identical groups are merged
 * via {@code EventGroupRegistry} and the NeoForge core plugin does not load on Fabric, there are no duplicates.
 * Payloads are neutral classes, and member names adhere to contract getter conventions.
 * SERVER scripts are initially loaded at {@code SERVER_STARTING} (this is equivalent to the NeoForge
 * datapack reload listener registration time: both occur after world loading completes and before {@code STARTED}).
 *
 * <p>On NeoForge, these buses pass native events directly. The payload members are isomorphic
 * (getServer/getPlayer), so scripts remain agnostic. Once NeoForge payloads are also switched to
 * neutral payloads in subsequent batches, they will be completely type-equivalent.
 *
 * <p><b>Known Fabric Timing Differences</b>: Fabric lacks an event equivalent to the NeoForge
 * "Starting" (which occurs after world loading but before completion). {@code starting} and
 * {@code aboutToStart} are both triggered at {@code SERVER_STARTING} (<b>before</b> world loading)
 * on Fabric. At this point, {@code server.getPlayerList()} is null. Please wait until {@code started}
 * to access the player list.
 */
public final class FabricServerEventBindings {

    /** Share the name with the NeoForge bindings/event/ServerEvents (Fabric subset). */
    public static final EventGroup SERVER_EVENTS = EventGroup.of("ServerEvents").expose(api -> api.versioned());

    public static final EventBusJS<ServerLifecycleEventJS, Void> ABOUT_TO_START =
            SERVER_EVENTS.server("aboutToStart", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTING =
            SERVER_EVENTS.server("starting", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTED =
            SERVER_EVENTS.server("started", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPING =
            SERVER_EVENTS.server("stopping", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPED =
            SERVER_EVENTS.server("stopped", ServerLifecycleEventJS.class);
    public static final EventBusJS<ServerTickEventJS, Void> TICK_PRE =
            SERVER_EVENTS.server("tickPre", ServerTickEventJS.class, api ->
                    api.stable(StableServerTickEvent.class,
                            event -> new StableServerTickEvent(event.getServer().getTickCount()))
                            .scriptTypes(ScriptType.SERVER));
    public static final EventBusJS<ServerTickEventJS, Void> TICK_POST =
            SERVER_EVENTS.server("tickPost", ServerTickEventJS.class, api ->
                    api.stable(StableServerTickEvent.class,
                            event -> new StableServerTickEvent(event.getServer().getTickCount()))
                            .scriptTypes(ScriptType.SERVER));

    // ---- Resource/Lifecycle (Batch 15; NeoForge passes native events, Fabric uses callbacks + neutral payload) ----

    /**
     * Map to {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS} for data pack synchronization
     * in server scripts. A null player represents all players after a reload (consistent with the NF OnDatapackSyncEvent).
     */
    public static final EventBusJS<DatapackSyncEventJS, Void> DATAPACK_SYNC =
            SERVER_EVENTS.server("datapackSync", DatapackSyncEventJS.class);

    /**
     * Map to {@code CommonLifecycleEvents.TAGS_LOADED} for tag updates in server scripts.
     * (The Fabric implementation is triggered via {@code ReloadableServerResourcesMixin} when server resources
     * are loaded, aligning with the NF TagsUpdatedEvent; native NF {@code UpdateCause} has no Fabric equivalent).
     */
    public static final EventBusJS<TagUpdatedEventJS, Void> TAGS_UPDATED =
            SERVER_EVENTS.server("tagsUpdated", TagUpdatedEventJS.class);

    /**
     * Map to {@code LootTableEvents.MODIFY} for loot table loading in server scripts.
     * Note semantic differences: NF allows whole-table get/set and is cancellable; Fabric uses
     * in-place builder modification and is not cancellable. This is documented in both the payload
     * Javadoc and the migration ledger.
     */
    public static final EventBusJS<LootTableLoadEventJS, Void> LOOT_TABLE_LOAD =
            SERVER_EVENTS.server("lootTableLoad", LootTableLoadEventJS.class);

    /** Share the name with the NeoForge bindings/event/PlayerEvents (Fabric subset). */
    public static final EventGroup PLAYER_EVENTS = EventGroup.of("PlayerEvents");

    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_IN =
            PLAYER_EVENTS.server("loggedIn", PlayerLifecycleEventJS.class);
    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_OUT =
            PLAYER_EVENTS.server("loggedOut", PlayerLifecycleEventJS.class);
    public static final EventBusJS<PlayerTickEventJS, Void> PLAYER_TICK_PRE =
            PLAYER_EVENTS.server("tickPre", PlayerTickEventJS.class);
    public static final EventBusJS<PlayerTickEventJS, Void> PLAYER_TICK_POST =
            PLAYER_EVENTS.server("tickPost", PlayerTickEventJS.class);
    public static final EventBusJS<PlayerCloneEventJS, Void> CLONED =
            PLAYER_EVENTS.server("cloned", PlayerCloneEventJS.class);
    public static final EventBusJS<PlayerRespawnEventJS, Void> RESPAWNED =
            PLAYER_EVENTS.server("respawned", PlayerRespawnEventJS.class);

    /** Chat: Neutral payload (player, username, message string; contract-compatible members). */
    public static final EventBusJS<com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS, Void> CHAT =
            PLAYER_EVENTS.server("chat", com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS.class);

    /** Wait for the player to post loggedIn at the end of the next tick (see timing notes in {@link #register}). */
    private static final Queue<ServerPlayer> PENDING_LOGINS = new ConcurrentLinkedQueue<>();

    /**
     * Current server instance (set on SERVER_STARTING, cleared on SERVER_STOPPED).
     * Fabric-API lacks an equivalent to {@code ServerLifecycleHooks#getCurrentServer}.
     * Recipe mixins use this to locate the server to find gamemaster players.
     */
    private static volatile MinecraftServer currentServer;

    /** @return Current server instance; null if not within the server lifecycle (e.g., pure client, no world loaded). */
    public static MinecraftServer currentServer() {
        return currentServer;
    }

    private FabricServerEventBindings() {}

    private static void drainPendingLogins() {
        ServerPlayer player;
        while ((player = PENDING_LOGINS.poll()) != null) {
            LOGGED_IN.post(new PlayerLifecycleEventJS(player));
        }
    }

    /**
     * @param loadServerScripts Action for initial SERVER script loading ({@code NekoRuntimeRoot.reload(SERVER)}).
     */
    public static void register(Runnable loadServerScripts,
            java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> rootSupplier) {
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot root = rootSupplier.get();
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector = root == null ? null
                    : root.domainCollector(com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner.DOMAIN);
            if (collector instanceof com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner) {
                owner.syncTo(handler.player);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().activateWorldPacks(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
            loadServerScripts.run();
            ABOUT_TO_START.post(new ServerLifecycleEventJS(server));
            // Same order as ServerEventListener#onServerAboutToStart on NeoForge: collect and apply item and
            // block property modifications after aboutToStart but before starting. The first collection
            // happens at generation; transaction reload replays are applied together in the DOMAIN_PLAN phase.
            com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot root = rootSupplier.get();
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector modificationCollector =
                    root == null ? null
                            : root.domainCollector(com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner.DOMAIN);
            if (modificationCollector instanceof com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner) {
                owner.applyInitialPlan(server);
            }
            STARTING.post(new ServerLifecycleEventJS(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // WorldLoader binds initial recipe resources before the runtime WORLD scripts activate.
            // Replay with the installed registries and player list before announcing script startup.
            if (!com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().worldPacks().isEmpty()
                    && server.getRecipeManager() instanceof com.tkisor.nekojs.api.recipe.IRecipeManagerExtension recipes) {
                try {
                    recipes.nekojs$applyScripts();
                } catch (Exception failure) {
                    com.tkisor.nekojs.NekoJS.LOGGER.error("[NEKO-1017] WORLD recipe replay failed after server startup", failure);
                    var root = rootSupplier.get();
                    if (root != null) root.errorTracker().recordCallbackError(
                            com.tkisor.nekojs.api.ScriptType.SERVER, "world_pack_recipes", failure);
                }
            }
            STARTED.post(new ServerLifecycleEventJS(server));
        });
        bridge.bind(STOPPING,
                listener -> ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                        listener.accept(new ServerLifecycleEventJS(server))));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // Logins not yet posted are not carried into the next server instance (e.g., singleplayer exiting and re-entering the world).
            PENDING_LOGINS.clear();
            currentServer = null;
            // Domain-specific server bindings expire when the server instance does (baseline is process-level exception, kept until root close).
            com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot root = rootSupplier.get();
            if (root != null) {
                com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector =
                        root.domainCollector(com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner.DOMAIN);
                if (collector instanceof com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner) {
                    owner.clearServer();
                }
            }
            STOPPED.post(new ServerLifecycleEventJS(server));
            if (root != null) root.mixins().clearCallbacks();
            var removed = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().deactivateWorldPacks();
            if (root != null) {
                root.clearWorldPackListeners(com.tkisor.nekojs.api.ScriptType.SERVER, removed);
                if (com.tkisor.nekojs.platform.Platform.isClient()) {
                    com.tkisor.nekojs.fabric.FabricClientReloadExecutor.execute(() -> {
                        // A delayed teardown must not revoke the next world's client generation.
                        if (com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().worldPacks().isEmpty()) {
                            root.clearWorldPackListeners(com.tkisor.nekojs.api.ScriptType.CLIENT, removed);
                        }
                    });
                }
            }
        });
        bridge.bindTransformed(
                TICK_PRE,
                listener -> ServerTickEvents.START_SERVER_TICK.register(listener::accept),
                ServerTickEventJS::new);
        // Player tick: Fabric-API has no per-player tick event; traverse at the start and end of server ticks
        // (NeoForge PlayerTickEvent.Pre/Post are also driven by server ticks, so timing is equivalent).
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PLAYER_TICK_PRE.post(new PlayerTickEventJS(player));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var root = rootSupplier.get();
            if (root != null && root.hasScriptType(ScriptType.SERVER)) {
                // The root flushes the legacy manager and every active package on the server thread.
                root.flushReadyNodeTimers(ScriptType.SERVER);
            }
            drainPendingLogins();
            TICK_POST.post(new ServerTickEventJS(server));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PLAYER_TICK_POST.post(new PlayerTickEventJS(player));
            }
        });
        // Clone/Respawn: Fabric COPY_FROM (data copy point, aligns with PlayerEvent.Clone) and
        // AFTER_RESPAWN (respawn completion). alive = old entity still alive (e.g., end return respawn).
        bridge.bind(CLONED,
                listener -> ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                        listener.accept(new PlayerCloneEventJS(newPlayer, oldPlayer, alive))));
        bridge.bind(RESPAWNED,
                listener -> ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                        listener.accept(new PlayerRespawnEventJS(newPlayer, oldPlayer, alive))));
        // Fabric's JOIN triggers in the middle of PlayerList#placeNewPlayer (semantic: "packets can be sent to this connection"),
        // at which point the player is not yet in server.getPlayerList()—NeoForge's PlayerLoggedInEvent is after entering the list.
        // Therefore, defer until the end of the next tick: otherwise, server-wide broadcasts made by scripts in loggedIn
        // (e.g., ClientData.sync) will silently miss this player. server.execute does not work—it is inline on the same thread.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_LOGINS.add(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Joined and left in the same tick: remove from pending loggedIn queue to avoid loggedOut preceding loggedIn.
            if (PENDING_LOGINS.remove(handler.player)) return;
            LOGGED_OUT.post(new PlayerLifecycleEventJS(handler.player));
        });
        // Datapack synchronization: player=null = all players after reload (semantic consistent with NF OnDatapackSyncEvent).
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, hasJoinedBefore) -> {
            MinecraftServer server = currentServer;
            if (server != null) {
                DATAPACK_SYNC.post(new DatapackSyncEventJS(player, server.getPlayerList()));
            }
        });
        // Tag updates: Fabric TAGS_LOADED (RegistryAccess, updated)—TAGS_UPDATED payload's
        // shouldUpdateStaticData carries updated (NF UpdateCause has no Fabric equivalent; differences recorded).
        bridge.bind(TAGS_UPDATED,
                listener -> CommonLifecycleEvents.TAGS_LOADED.register((registries, updated) ->
                        listener.accept(new TagUpdatedEventJS(registries, updated))));
        // Loot table load: per-table MODIFY (builder pattern; NF is whole-table get/set + cancellable; differences recorded).
        bridge.bind(LOOT_TABLE_LOAD,
                listener -> LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) ->
                        listener.accept(new LootTableLoadEventJS(
                                key.identifier(), tableBuilder, registries))));
        bridge.bind(CHAT,
                listener -> net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register(
                        (message, sender, params) -> listener.accept(
                                new com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS(
                                        sender, message.signedContent()))));
    }
}
