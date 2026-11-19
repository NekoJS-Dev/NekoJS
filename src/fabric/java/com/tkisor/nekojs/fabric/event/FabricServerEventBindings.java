package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableDatapackSyncEvent;
import com.tkisor.nekojs.api.event.StableLootTableLoadEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StableServerLifecycleEvent;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.api.event.StableTagsUpdatedEvent;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.bindings.event.ServerLootReloadExecutor;
import com.tkisor.nekojs.core.stage.gate.StageCandidatePlan;
import com.tkisor.nekojs.fabric.FabricPDataSync;
import com.tkisor.nekojs.fabric.FabricPlayNetwork;
import com.tkisor.nekojs.stage.StageDomainOwner;
import com.tkisor.nekojs.wrapper.event.player.PlayerCloneEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerRespawnEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerTickEventJS;
import com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS;
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
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Provides Fabric bridges for server events: lifecycle (aboutToStart, starting, started, stopping, stopped),
 * server ticks (tickPre, tickPost), and player connection events (loggedIn, loggedOut).
 *
 * <p>The group and bus names match the shared event groups. Since identical groups are merged
 * via {@code EventGroupRegistry} and the shared core plugin does not load on Fabric, there are no duplicates.
 * Payloads are neutral classes, and member names adhere to contract getter conventions.
 * SERVER scripts are initially loaded at {@code SERVER_STARTING} before {@code STARTED}.
 *
 * <p><b>Known Fabric Timing Differences</b>: {@code starting} and {@code aboutToStart} are both
 * triggered at {@code SERVER_STARTING} (<b>before</b> world loading) on Fabric. At this point,
 * {@code server.getPlayerList()} may not be populated yet; wait until {@code started} to access
 * online players.
 */
public final class FabricServerEventBindings {

    /** Shares the group name with bindings/event/ServerEvents (Fabric subset). */
    public static final EventGroup SERVER_EVENTS = EventGroup.of("ServerEvents");

    public static final EventBusJS<ServerLifecycleEventJS, Void> ABOUT_TO_START =
            SERVER_EVENTS.server("aboutToStart", ServerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTransition));
                api.stable().when(event -> event instanceof StableServerTransition && event.getServer().isSameThread())
                        .view(StableServerLifecycleEvent.class,
                        e -> new StableServerLifecycleEvent("about_to_start", e.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTING =
            SERVER_EVENTS.server("starting", ServerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTransition));
                api.stable().when(event -> event instanceof StableServerTransition && event.getServer().isSameThread())
                        .view(StableServerLifecycleEvent.class,
                        e -> new StableServerLifecycleEvent("starting", e.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerLifecycleEventJS, Void> STARTED =
            SERVER_EVENTS.server("started", ServerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTransition));
                api.stable().when(event -> event instanceof StableServerTransition && event.getServer().isSameThread())
                        .view(StableServerLifecycleEvent.class,
                        e -> new StableServerLifecycleEvent("started", e.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPING =
            SERVER_EVENTS.server("stopping", ServerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTransition));
                api.stable().when(event -> event instanceof StableServerTransition && event.getServer().isSameThread())
                        .view(StableServerLifecycleEvent.class,
                        e -> new StableServerLifecycleEvent("stopping", e.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerLifecycleEventJS, Void> STOPPED =
            SERVER_EVENTS.server("stopped", ServerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTransition));
                api.stable().when(event -> event instanceof StableServerTransition && event.getServer().isSameThread())
                        .view(StableServerLifecycleEvent.class,
                        e -> new StableServerLifecycleEvent("stopped", e.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerTickEventJS, Void> TICK_PRE =
            SERVER_EVENTS.server("tickPre", ServerTickEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableServerTickPre));
                api.stable().when(event -> event instanceof StableServerTickPre && event.getServer().isSameThread())
                        .view(StableServerTickEvent.class,
                        event -> new StableServerTickEvent(event.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<ServerTickEventJS, Void> TICK_POST =
            SERVER_EVENTS.server("tickPost", ServerTickEventJS.class, api -> {
                api.versioned();
                api.stable().when(event -> event.getServer().isSameThread())
                        .view(StableServerTickEvent.class,
                        event -> new StableServerTickEvent(event.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });

    // ---- Resource/Lifecycle ----

    /**
     * Maps to {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS} for data pack synchronization
     * in server scripts. A null player represents all players after a reload.
     */
    public static final EventBusJS<DatapackSyncEventJS, Void> DATAPACK_SYNC =
            SERVER_EVENTS.server("datapackSync", DatapackSyncEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StableDatapackSyncDispatch));
                api.stable()
                        .when(event -> event instanceof StableDatapackSyncDispatch
                                && event.getPlayerList().getServer().isSameThread())
                        .view(StableDatapackSyncEvent.class, e -> new StableDatapackSyncEvent(
                                e.getPlayer() == null ? "" : e.getPlayer().getScoreboardName(),
                                e.getPlayer() == null ? e.getPlayerList().getPlayers().size() : 1,
                                e.getPlayer() == null))
                        .scriptTypes(ScriptType.SERVER);
            });

    public static void postStableDatapackSync(PlayerList playerList, ServerPlayer player) {
        if (DATAPACK_SYNC.hasStableModuleListeners(ScriptType.SERVER)) {
            DATAPACK_SYNC.postStableModuleNotification(new StableDatapackSyncDispatch(player, playerList), ScriptType.SERVER);
        }
    }

    private static final class StableDatapackSyncDispatch extends DatapackSyncEventJS {
        private StableDatapackSyncDispatch(ServerPlayer player, PlayerList playerList) {
            super(player, playerList);
        }
    }

    /**
     * Maps to {@code CommonLifecycleEvents.TAGS_LOADED} for tag updates in server scripts.
     */
    public static final EventBusJS<TagUpdatedEventJS, Void> TAGS_UPDATED =
            SERVER_EVENTS.server("tagsUpdated", TagUpdatedEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event instanceof StableTagsUpdate)
                        .view(StableTagsUpdatedEvent.class, e -> new StableTagsUpdatedEvent("SERVER_DATA_LOAD", false))
                        .scriptTypes(ScriptType.SERVER);
            });

    public static void postStableTagsUpdated(ReloadableServerResources resources) {
        MinecraftServer server = currentServer;
        if (server == null || !server.isSameThread()) return;
        if (server.getRecipeManager() != resources.getRecipeManager()) return;
        TAGS_UPDATED.postStableModuleNotification(new StableTagsUpdate(server), ScriptType.SERVER);
    }

    private static final class StableTagsUpdate extends TagUpdatedEventJS {
        private StableTagsUpdate(MinecraftServer server) {
            super(server.registryAccess(), false);
        }
    }

    /**
     * Maps to {@code LootTableEvents.MODIFY} for loot table loading in server scripts.
     * Fabric uses in-place builder modification and is not cancellable.
     */
    public static final EventBusJS<LootTableLoadEventJS, Void> LOOT_TABLE_LOAD =
            SERVER_EVENTS.add("lootTableLoad", ScriptType.SERVER, EventBusJS.of(LootTableLoadEventJS.class, true), api -> {
                api.versioned().notificationOnly();
                api.stable().when(event -> ServerLootReloadExecutor.isStableDispatch())
                        .view(StableLootTableLoadEvent.class,
                        e -> new StableLootTableLoadEvent(e.getId() == null ? "" : e.getId().toString()))
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Shares the group name with bindings/event/PlayerEvents (Fabric subset). */
    public static final EventGroup PLAYER_EVENTS = EventGroup.of("PlayerEvents");

    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_IN =
            PLAYER_EVENTS.server("loggedIn", PlayerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StablePlayerLifecycleDispatch));
                api.stable()
                        .when(event -> event instanceof StablePlayerLifecycleDispatch && isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerLifecycle)
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerLifecycleEventJS, Void> LOGGED_OUT =
            PLAYER_EVENTS.server("loggedOut", PlayerLifecycleEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StablePlayerLifecycleDispatch));
                api.stable()
                        .when(event -> event instanceof StablePlayerLifecycleDispatch && isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerLifecycle)
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerTickEventJS, Void> PLAYER_TICK_PRE =
            PLAYER_EVENTS.server("tickPre", PlayerTickEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerTick)
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerTickEventJS, Void> PLAYER_TICK_POST =
            PLAYER_EVENTS.server("tickPost", PlayerTickEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerTick)
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerCloneEventJS, Void> CLONED =
            PLAYER_EVENTS.server("cloned", PlayerCloneEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerClone)
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerRespawnEventJS, Void> RESPAWNED =
            PLAYER_EVENTS.server("respawned", PlayerRespawnEventJS.class, api -> {
                api.versioned().when(event -> !(event instanceof StablePlayerRespawnDispatch));
                api.stable()
                        .when(event -> event instanceof StablePlayerRespawnDispatch && isCurrentPlayerTransition(event.getPlayer()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerRespawn)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Chat: neutral payload (player, username, message string; contract-compatible members). */
    public static final EventBusJS<ServerChatEventJS, Void> CHAT =
            PLAYER_EVENTS.add("chat", ScriptType.SERVER, EventBusJS.of(ServerChatEventJS.class, true), api -> {
                api.versioned().notificationOnly().when(event -> !(event instanceof StableChatDispatch));
                api.stable()
                        .when(event -> event instanceof StableChatDispatch)
                        .view(StablePlayerChatEvent.class, FabricStableEventProjections::playerChat)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static boolean postStableChat(ServerPlayer player, String rawText) {
        return ServerLevelEventThread.isCurrent(player.level()) && CHAT.hasStableModuleListeners(ScriptType.SERVER)
                && CHAT.postStableModuleCancellable(new StableChatDispatch(player, rawText), ScriptType.SERVER);
    }

    private static final class StableChatDispatch extends ServerChatEventJS {
        private final String username;

        private StableChatDispatch(ServerPlayer player, String rawText) {
            super(player, rawText);
            this.username = player.getGameProfile().name();
        }

        @Override
        public String getUsername() {
            return username;
        }
    }

    /** Waits for the player to post loggedIn at the end of the next tick (see timing notes in {@link #register}). */
    private static final Queue<ServerPlayer> PENDING_LOGINS = new ConcurrentLinkedQueue<>();

    private static java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> runtimeRoot = () -> null;

    /**
     * Current server instance (set on SERVER_STARTING, cleared on SERVER_STOPPED).
     * Recipe mixins use this to locate the server when finding gamemaster players.
     */
    private static volatile MinecraftServer currentServer;

    /** @return Current server instance; null if not within the server lifecycle (e.g., pure client, no world loaded). */
    public static MinecraftServer currentServer() {
        return currentServer;
    }

    public interface LootTableCancellation {
        void nekojs$omitLootTable();

        static LootTable omittedTable() {
            return OmittedLootTable.VALUE;
        }

        static boolean isOmitted(Object value) {
            return value instanceof LootTable && value == OmittedLootTable.VALUE;
        }
    }

    private static final class OmittedLootTable {
        private static final LootTable VALUE = LootTable.lootTable().build();
    }

    public static void postStableTickPre(MinecraftServer server) {
        if (TICK_PRE.hasStableModuleListeners(ScriptType.SERVER)) {
            TICK_PRE.postStableModuleNotification(new StableServerTickPre(server), ScriptType.SERVER);
        }
    }

    private static final class StableServerTickPre extends ServerTickEventJS {
        private StableServerTickPre(MinecraftServer server) {
            super(server);
        }
    }

    private FabricServerEventBindings() {}

    public static void postStableAboutToStart(MinecraftServer server) {
        if (ABOUT_TO_START.hasStableModuleListeners(ScriptType.SERVER)) {
            ABOUT_TO_START.postStableModuleNotification(new StableServerTransition(server), ScriptType.SERVER);
        }
    }

    public static void postStableStarting(MinecraftServer server) {
        if (STARTING.hasStableModuleListeners(ScriptType.SERVER)) {
            STARTING.postStableModuleNotification(new StableServerTransition(server), ScriptType.SERVER);
        }
    }

    public static void postStableStarted(MinecraftServer server) {
        var rootSupplier = runtimeRoot;
        if (resolveStageDomainOwner(rootSupplier) == null
                && server.getRecipeManager() instanceof com.tkisor.nekojs.api.recipe.IRecipeManagerExtension recipes) {
            try {
                recipes.nekojs$applyScripts();
                StageDomainOwner stageOwner = resolveStageDomainOwner(rootSupplier);
                if (stageOwner != null) {
                    stageOwner.refreshInlineRecipesAndSync(server);
                }
            } catch (Exception failure) {
                com.tkisor.nekojs.NekoJS.LOGGER.error("[NEKO-1017] WORLD recipe replay failed after server startup", failure);
                var root = rootSupplier.get();
                if (root != null) root.errorTracker().recordCallbackError(
                        com.tkisor.nekojs.api.ScriptType.SERVER, "world_pack_recipes", failure);
            }
        }
        if (STARTED.hasStableModuleListeners(ScriptType.SERVER)) {
            STARTED.postStableModuleNotification(new StableServerTransition(server), ScriptType.SERVER);
        }
    }

    public static void postStableStopping(MinecraftServer server) {
        if (STOPPING.hasStableModuleListeners(ScriptType.SERVER)) {
            STOPPING.postStableModuleNotification(new StableServerTransition(server), ScriptType.SERVER);
        }
    }

    private static final ThreadLocal<MinecraftServer> DEFERRED_SERVER_STOP = new ThreadLocal<>();

    public static void runServerStop(MinecraftServer server, Runnable stop) {
        MinecraftServer previous = DEFERRED_SERVER_STOP.get();
        DEFERRED_SERVER_STOP.set(server);
        try {
            stop.run();
        } finally {
            if (previous == null) DEFERRED_SERVER_STOP.remove();
            else DEFERRED_SERVER_STOP.set(previous);
        }
    }

    public static boolean isStopDeferred(MinecraftServer server) {
        return DEFERRED_SERVER_STOP.get() == server;
    }

    public static void postStableStopped(MinecraftServer server) {
        var rootSupplier = runtimeRoot;
        try {
            if (STOPPED.hasStableModuleListeners(ScriptType.SERVER)) {
                STOPPED.postStableModuleNotification(new StableServerTransition(server), ScriptType.SERVER);
            }
        } finally {
            FabricPlayNetwork.releaseServer(server);
            FabricPDataSync.releaseServer(server);
            if (currentServer == server) {
                try {
                    clearStoppedServerAccess(rootSupplier);
                } finally {
                    releaseStoppedScripts(rootSupplier);
                }
            }
        }
    }

    private static void clearStoppedServerAccess(
            java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> rootSupplier) {
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
    }

    private static void releaseStoppedScripts(
            java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> rootSupplier) {
        var root = rootSupplier.get();
        StageDomainOwner stageOwner = resolveStageDomainOwner(rootSupplier);
        if (stageOwner != null) {
            stageOwner.onServerStopped();
        }
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
    }

    private static final class StableServerTransition extends ServerLifecycleEventJS {
        private StableServerTransition(MinecraftServer server) {
            super(server);
        }
    }

    private static StageDomainOwner resolveStageDomainOwner(
            java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> rootSupplier) {
        com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot root = rootSupplier.get();
        if (root != null) {
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector =
                    root.domainCollector(StageCandidatePlan.DOMAIN);
            if (collector instanceof StageDomainOwner owner) {
                return owner;
            }
        }
        return StageDomainOwner.current();
    }

    private static boolean isCurrentPlayerTransition(Player player) {
        return player instanceof ServerPlayer serverPlayer && ServerLevelEventThread.isCurrent(serverPlayer.level());
    }

    private static final class StablePlayerLifecycleDispatch extends PlayerLifecycleEventJS {
        private StablePlayerLifecycleDispatch(ServerPlayer player) {
            super(player);
        }
    }

    private static final class StablePlayerRespawnDispatch extends PlayerRespawnEventJS {
        private StablePlayerRespawnDispatch(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
            super(newPlayer, oldPlayer, alive);
        }
    }

    private static void drainPendingLogins() {
        ServerPlayer player;
        while ((player = PENDING_LOGINS.poll()) != null) {
            LOGGED_IN.post(new PlayerLifecycleEventJS(player));
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        StageDomainOwner stageOwner = resolveStageDomainOwner(runtimeRoot);
        if (stageOwner != null) stageOwner.onPlayerJoin(player);
        if (LOGGED_IN.hasStableModuleListeners(ScriptType.SERVER)) {
            LOGGED_IN.postStableModuleNotification(new StablePlayerLifecycleDispatch(player), ScriptType.SERVER);
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        StageDomainOwner stageOwner = resolveStageDomainOwner(runtimeRoot);
        if (stageOwner != null) stageOwner.onPlayerLeave(player);
        if (LOGGED_OUT.hasStableModuleListeners(ScriptType.SERVER)) {
            LOGGED_OUT.postStableModuleNotification(new StablePlayerLifecycleDispatch(player), ScriptType.SERVER);
        }
    }

    public static void onPlayerRespawned(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        StageDomainOwner stageOwner = resolveStageDomainOwner(runtimeRoot);
        if (stageOwner != null) stageOwner.onPlayerRespawn(newPlayer);
        if (RESPAWNED.hasStableModuleListeners(ScriptType.SERVER)) {
            RESPAWNED.postStableModuleNotification(new StablePlayerRespawnDispatch(oldPlayer, newPlayer, alive), ScriptType.SERVER);
        }
    }

    /**
     * @param loadServerScripts Action for initial SERVER script loading ({@code NekoRuntimeRoot.reload(SERVER)}).
     */
    public static void register(Runnable loadServerScripts,
            java.util.function.Supplier<com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot> rootSupplier) {
        runtimeRoot = rootSupplier;
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot root = rootSupplier.get();
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector = root == null ? null
                    : root.domainCollector(com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner.DOMAIN);
            if (collector instanceof com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner) {
                owner.syncTo(handler.getPlayer());
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().activateWorldPacks(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
            StageDomainOwner stageOwner = resolveStageDomainOwner(rootSupplier);
            if (stageOwner != null) stageOwner.bindServer(server);
            loadServerScripts.run();
            FabricCommandEventBindings.postInitialRegistration(server);
            if (stageOwner != null) stageOwner.onServerAboutToStart(server);
            ABOUT_TO_START.post(new ServerLifecycleEventJS(server));
            // Collect and apply item and block property modifications after aboutToStart and before starting.
            // The first collection happens at generation; transaction reload replays are applied together
            // in the DOMAIN_PLAN phase.
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
            STARTED.post(new ServerLifecycleEventJS(server));
        });
        bridge.bind(STOPPING,
                listener -> ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                        listener.accept(new ServerLifecycleEventJS(server))));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StageDomainOwner stageOwner = resolveStageDomainOwner(rootSupplier);
            if (stageOwner != null) {
                stageOwner.onServerStopping(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            boolean releaseNow = !isStopDeferred(server) && currentServer == server;
            try {
                if (releaseNow) clearStoppedServerAccess(rootSupplier);
                STOPPED.post(new ServerLifecycleEventJS(server));
            } finally {
                if (releaseNow) releaseStoppedScripts(rootSupplier);
            }
        });
        bridge.bindTransformed(
                TICK_PRE,
                listener -> ServerTickEvents.START_SERVER_TICK.register(listener::accept),
                ServerTickEventJS::new);
        // Player tick: Fabric-API has no per-player tick event; traverse at the start and end of server ticks.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var root = rootSupplier.get();
            if (root != null && root.hasScriptType(ScriptType.SERVER)) {
                // The root flushes the legacy manager and every active package on the server thread.
                root.flushReadyNodeTimers(ScriptType.SERVER);
            }
            drainPendingLogins();
            TICK_POST.post(new ServerTickEventJS(server));
            StageDomainOwner stageOwner = resolveStageDomainOwner(rootSupplier);
            if (stageOwner != null) {
                stageOwner.onServerTickPost(server);
            }
        });
        // Clone/Respawn: Fabric COPY_FROM (data copy point) and AFTER_RESPAWN (respawn completion).
        // alive = old entity still alive (e.g., End return respawn).
        bridge.bind(CLONED,
                listener -> ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                        listener.accept(new PlayerCloneEventJS(newPlayer, oldPlayer, alive))));
        bridge.bind(RESPAWNED,
                listener -> ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                        listener.accept(new PlayerRespawnEventJS(newPlayer, oldPlayer, alive))));
        // Fabric's JOIN triggers in the middle of PlayerList#placeNewPlayer (semantic: "packets can be sent to this connection"),
        // at which point the player is not yet in server.getPlayerList().
        // Defer until the end of the next tick so server-wide broadcasts made by scripts in loggedIn
        // (e.g., ClientData.sync) include this player.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PENDING_LOGINS.add(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Joined and left in the same tick: remove from pending loggedIn queue to avoid loggedOut preceding loggedIn.
            if (PENDING_LOGINS.remove(handler.player)) return;
            LOGGED_OUT.post(new PlayerLifecycleEventJS(handler.player));
        });
        // Datapack synchronization: player=null = all players after reload.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            MinecraftServer server = currentServer;
            if (server != null && DATAPACK_SYNC.hasListeners()) {
                DATAPACK_SYNC.post(new DatapackSyncEventJS(player, server.getPlayerList()));
            }
        });
        // Tag updates: Fabric TAGS_LOADED (RegistryAccess, updated).
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (TAGS_UPDATED.hasListeners()) TAGS_UPDATED.post(new TagUpdatedEventJS(registries, client));
        });
        // Loot table load: per-table MODIFY (builder pattern).
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!LOOT_TABLE_LOAD.hasListeners()) return;
            boolean cancelled = LOOT_TABLE_LOAD.postWithStableModuleCancellation(
                    new LootTableLoadEventJS(key.identifier(), tableBuilder, registries), ScriptType.SERVER);
            if (cancelled && ServerLootReloadExecutor.isStableDispatch()) {
                ((LootTableCancellation) tableBuilder).nekojs$omitLootTable();
            }
        });
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (CHAT.hasListeners()) {
                CHAT.postWithStableModuleCancellation(new ServerChatEventJS(sender, message.signedContent()), ScriptType.SERVER);
            }
        });
    }
}
