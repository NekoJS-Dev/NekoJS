package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.stage.StableStageGatesEvent;
import com.tkisor.nekojs.api.stage.StageCustomizeEvent;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageEvents;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.gate.RegionShape;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RegionGate;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.PackActivationCoordinator;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeManager;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.stage.StagePlayerRefAdapter;
import com.tkisor.nekojs.core.stage.StageRuntimeBridge;
import com.tkisor.nekojs.core.stage.StageStore;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.core.stage.gate.StageCandidatePlan;
import com.tkisor.nekojs.core.stage.gate.StageWireProtocol;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared server-side domain owner coordinating stage persistence ({@link StageStore}),
 * transactional {@code StageEvents.customize} and {@code StageEvents.gates} candidate collection,
 * inline recipe stage merges, client gate summary broadcasts, and periodic player gate enforcement.
 */
public final class StageDomainOwner implements CandidateDomainCollector, AutoCloseable {

    /** Domain identifier matching {@link StageCandidatePlan#DOMAIN}. */
    public static final String DOMAIN = StageCandidatePlan.DOMAIN;

    private static final EquipmentSlot[] GATED_EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    private static final AtomicBoolean HOST_RESOLVER_REGISTERED = new AtomicBoolean(false);
    private static volatile @Nullable StageDomainOwner CURRENT;
    private static volatile CompiledStageGateSnapshot ACTIVE_SNAPSHOT = CompiledStageGateSnapshot.EMPTY;

    static {
        registerHostResolverOnce();
    }

    private final MinecraftStageRegistryAccess registryAccess = new MinecraftStageRegistryAccess();
    private final Map<UUID, LastSafePos> lastSafePositions = new ConcurrentHashMap<>();
    private final Runnable inlineRecipeListener;

    private volatile @Nullable MinecraftServer boundServer;
    private volatile @Nullable StageStore store;
    private volatile CompiledStageGateSnapshot baseSnapshot;
    private volatile CompiledStageGateSnapshot snapshot;
    private @Nullable String broadcastGateBaseline;
    private boolean worldPlanPublished;
    private @Nullable CandidateRecipeReplay.AcceptedTable acceptedRecipes;
    private @Nullable BootstrapStagePlan bootstrapPlan;
    private @Nullable NekoRuntimeRoot reloadRoot;
    private final Map<ResourceManager, ResourceAttempt> resourceAttempts = new java.util.IdentityHashMap<>();
    private final Map<ReloadableServerResources, ResourceAttempt> incomingAttempts = new java.util.IdentityHashMap<>();
    private final ThreadLocal<IncomingResources> incomingResources = new ThreadLocal<>();

    private record IncomingResources(RecipeManager recipes, RegistryAccess registries) {}

    private record BootstrapStagePlan(RecipeManager manager, List<StageDefinition> definitions) {}

    private static final class ResourceAttempt {
        final ResourceManager source;
        final ReloadableServerResources incoming;
        final java.util.concurrent.Executor apply;
        final RegistryAccess registries;
        @Nullable PackActivationCoordinator.PreparedReload pending;
        boolean rejected;

        ResourceAttempt(ResourceManager source, ReloadableServerResources incoming, java.util.concurrent.Executor apply,
                        RegistryAccess registries) {
            this.source = source;
            this.incoming = incoming;
            this.apply = apply;
            this.registries = registries;
        }
    }

    /** Creates a new stage domain owner and installs it as the active owner instance. */
    public StageDomainOwner() {
        this.baseSnapshot = CompiledStageGateSnapshot.compile(
                List.of(),
                Set.of(),
                Map.of(),
                this.registryAccess
        );
        this.snapshot = this.baseSnapshot;
        ACTIVE_SNAPSHOT = this.snapshot;
        CURRENT = this;
        this.inlineRecipeListener = () -> refreshInlineRecipesAndSync(this.boundServer);
        InlineRecipeStageRegistry.SHARED.onUpdated(this.inlineRecipeListener);
    }

    public void bindReloadRoot(NekoRuntimeRoot root) {
        this.reloadRoot = root;
    }

    public synchronized void trackResourceAttempt(ResourceManager source, ReloadableServerResources incoming,
                                                  java.util.concurrent.Executor apply, RegistryAccess registries) {
        var attempt = new ResourceAttempt(source, incoming, apply, registries);
        this.resourceAttempts.put(source, attempt);
        this.incomingAttempts.put(incoming, attempt);
    }

    public void dispatchResourceScripts(ReloadableServerResources incoming,
            java.util.function.Consumer<PackActivationCoordinator.PreparedReload> dispatch) {
        ResourceAttempt attempt;
        synchronized (this) {
            attempt = this.incomingAttempts.get(incoming);
        }
        Runnable action = () -> {
            PackActivationCoordinator.PreparedReload pending = null;
            try {
                pending = prepareResourceScripts(incoming);
                if (pending != null && !pending.prepared()) {
                    NekoJS.LOGGER.error("[NEKO-4071] Native reload could not prepare a script candidate: {}", pending.commit().failure());
                    rejectResourceScripts(incoming);
                    pending = null;
                }
            } catch (Exception failure) {
                rejectResourceScripts(incoming);
                NekoJS.LOGGER.error("[NEKO-4071] Native reload could not prepare a script candidate", failure);
            }
            try {
                dispatch.accept(pending);
            } catch (Exception failure) {
                rejectResourceScripts(incoming);
                NekoJS.LOGGER.error("[NEKO-4071] Native reload script candidate rejected during resource preparation", failure);
            }
        };
        if (attempt != null && attempt.apply instanceof MinecraftServer server && !server.isSameThread()) {
            server.submit(action).join();
        } else {
            action.run();
        }
    }

    private @Nullable PackActivationCoordinator.PreparedReload prepareResourceScripts(ReloadableServerResources incoming) {
        ResourceAttempt attempt;
        synchronized (this) {
            attempt = this.incomingAttempts.get(incoming);
        }
        if (attempt == null || this.reloadRoot == null) return null;
        PackActivationCoordinator.PreparedReload pending = this.reloadRoot.prepareServerResourceScripts();
        synchronized (this) {
            if (this.incomingAttempts.get(incoming) == attempt && attempt.pending == null && !attempt.rejected) {
                attempt.pending = pending;
                return pending;
            }
        }
        pending.close();
        return null;
    }

    private void rejectResourceScripts(ReloadableServerResources incoming) {
        PackActivationCoordinator.PreparedReload pending;
        synchronized (this) {
            var attempt = this.incomingAttempts.get(incoming);
            if (attempt == null) return;
            attempt.rejected = true;
            pending = attempt.pending;
            attempt.pending = null;
        }
        if (pending != null) pending.close();
    }

    public void abortResourceLoad(ResourceManager source) {
        ResourceAttempt attempt;
        synchronized (this) {
            attempt = this.resourceAttempts.get(source);
        }
        if (attempt != null) abortResourceBinding(attempt.incoming);
    }

    public void abortResourceBinding(ReloadableServerResources incoming) {
        ResourceAttempt attempt = takeResourceAttempt(incoming);
        if (attempt != null && attempt.pending != null) attempt.pending.close();
    }

    private synchronized @Nullable ResourceAttempt takeResourceAttempt(ReloadableServerResources incoming) {
        ResourceAttempt attempt = this.incomingAttempts.remove(incoming);
        if (attempt != null) this.resourceAttempts.remove(attempt.source, attempt);
        return attempt;
    }

    public boolean finishResourceScripts(ReloadableServerResources incoming, RecipeManager manager) {
        ResourceAttempt attempt = takeResourceAttempt(incoming);
        NekoRuntimeRoot root = this.reloadRoot;
        if (root == null) return false;
        MinecraftServer server = this.boundServer;
        if (server == null ? attempt == null : !server.isSameThread() || server.getRecipeManager() != manager) {
            if (attempt != null && attempt.pending != null) attempt.pending.close();
            return true;
        }
        if (attempt != null && attempt.rejected) {
            restoreAcceptedRecipes(manager);
            return true;
        }
        try (var pending = attempt != null && attempt.pending != null ? attempt.pending
                : root.prepareServerResourceScripts()) {
            this.incomingResources.set(new IncomingResources(manager,
                    attempt != null ? attempt.registries : server.registryAccess()));
            try {
                var result = root.commitServerResourceScripts(pending);
                if (result.success()) return true;
                NekoJS.LOGGER.error("[NEKO-4071] Native resources were installed but the NekoJS script and Stage candidate was rejected: {}", result.failure());
            } finally {
                this.incomingResources.remove();
            }
        } catch (Exception failure) {
            NekoJS.LOGGER.error("[NEKO-4071] Native resources were installed but NekoJS could not activate the Stage candidate", failure);
        }
        restoreAcceptedRecipes(manager);
        return true;
    }

    private void restoreAcceptedRecipes(RecipeManager manager) {
        var accepted = this.acceptedRecipes;
        if (accepted == null) return;
        if (!(manager instanceof CandidateRecipeReplay replay)) {
            NekoJS.LOGGER.error("[NEKO-4071] Accepted script recipes could not be retained: incoming recipe manager has no replay adapter");
            return;
        }
        try {
            replay.nekojs$restoreAcceptedTable(accepted);
        } catch (RuntimeException incompatible) {
            NekoJS.LOGGER.error("[NEKO-4071] Accepted script recipes are incompatible with the installed native registries; keeping the native recipe table", incompatible);
        }
    }

    private void closeResourceAttempts() {
        List<ReloadableServerResources> resources;
        synchronized (this) {
            resources = List.copyOf(this.incomingAttempts.keySet());
        }
        resources.forEach(this::abortResourceBinding);
    }

    private static void registerHostResolverOnce() {
        if (HOST_RESOLVER_REGISTERED.compareAndSet(false, true)) {
            StagePlayerRefAdapter.registerHostResolver(host -> {
                if (!(host instanceof Player player)) {
                    return null;
                }
                if (StageGateEnforcer.isFakePlayer(player)) {
                    return StagePlayerRef.FAKE;
                }
                if (player.level() != null && player.level().isClientSide()) {
                    return StagePlayerRef.client(player.getStringUUID(), player.getScoreboardName());
                }
                return StagePlayerRef.of(player.getStringUUID(), player.getScoreboardName());
            });
        }
    }

    /** Returns the currently active {@link StageDomainOwner}, or {@code null} if closed/unregistered. */
    public static @Nullable StageDomainOwner current() {
        return CURRENT;
    }

    /** Returns the currently active compiled stage gate snapshot (never {@code null}). */
    public static CompiledStageGateSnapshot activeSnapshot() {
        StageDomainOwner owner = CURRENT;
        return owner != null ? owner.snapshot : ACTIVE_SNAPSHOT;
    }

    /** Returns the active world-backed {@link StageStore}, or the fallback in-memory bridge store. */
    public static StageStore activeStore() {
        StageDomainOwner owner = CURRENT;
        if (owner != null) {
            StageStore bound = owner.store;
            if (bound != null) {
                return bound;
            }
        }
        return StageRuntimeBridge.store();
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    @Override
    public void collect(CandidateDomainCollector.Handle handle) {
        if (handle.scriptType() != ScriptType.SERVER) {
            return;
        }
        StageCustomizeEvent customizeEvent = new StageCustomizeEvent();
        StableStageGatesEvent gatesEvent = new StableStageGatesEvent();
        handle.dispatch(StageEvents.CUSTOMIZE, customizeEvent);
        handle.dispatch(StageEvents.GATES, gatesEvent);

        MinecraftServer server = this.boundServer;
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("[NEKO-4071] Stage and recipe collection must run on the server owner thread");
        }
        IncomingResources incoming = this.incomingResources.get();
        RecipeManager manager = incoming != null ? incoming.recipes() : server == null ? null : server.getRecipeManager();
        RegistryAccess candidateRegistries = incoming != null ? incoming.registries()
                : server == null ? this.registryAccess.boundRegistries() : server.registryAccess();
        if ((incoming != null || server != null) && !(manager instanceof CandidateRecipeReplay)) {
            throw new IllegalStateException("[NEKO-4071] The live recipe manager cannot collect transactional recipe declarations");
        }
        CandidateRecipeReplay.Prepared recipes = manager instanceof CandidateRecipeReplay replay
                ? replay.nekojs$prepareScriptReplay(event -> handle.dispatch(ServerEvents.RECIPES, event))
                : null;
        var inline = new InlineRecipeStageRegistry();
        inline.replaceSnapshot(recipes == null ? InlineRecipeStageRegistry.SHARED.snapshot() : recipes.stages(), false);
        StageCandidatePlan plan = new StageCandidatePlan(
                null,
                null,
                null,
                activeStore(),
                inline,
                recipes == null ? this.registryAccess
                        : this.registryAccess.withRecipeDescriptors(candidateRegistries, recipes.descriptors()),
                (definitions, compiled) -> {
                    if (recipes != null) {
                        recipes.publish().run();
                        this.acceptedRecipes = recipes.table();
                        if (server == null && incoming != null) {
                            this.bootstrapPlan = new BootstrapStagePlan(manager, definitions);
                        }
                        this.registryAccess.bindRegistries(candidateRegistries);
                        this.registryAccess.bindRecipeDescriptors(recipes.descriptors());
                        InlineRecipeStageRegistry.SHARED.replaceSnapshot(recipes.stages(), false);
                    }
                    installSnapshot(compiled);
                }
        );
        try {
            plan.addDefinitions(customizeEvent.buildDefinitions());
            plan.addDeclarations(gatesEvent.buildDeclarations());
            plan.addWhitelistedContainers(gatesEvent.whitelistedContainers());
            plan.addWhitelistedContainerSlots(gatesEvent.whitelistedContainerSlots());
        } catch (Throwable t) {
            plan.fail(t.getMessage(), t);
        }
        handle.registerPlan(new CandidateStatePlan() {
            @Override public String domain() { return plan.domain(); }
            @Override public void preflight() { plan.preflight(); }
            @Override public void publish() { plan.publish(); }
            @Override public void discard() { plan.discard(); }
            @Override public void afterCommit() {
                try {
                    if (recipes != null) recipes.afterCommit().accept(incoming == null ? server : null);
                } finally {
                    broadcastGateSummary(server, snapshot);
                }
            }
        });
    }

    /**
     * Collects, preflights, and publishes the initial stage customization and gate plan against
     * active listeners once server registries and world stage storage are bound.
     */
    public void applyInitialPlan(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        this.boundServer = server;
        this.registryAccess.bindRegistries(server.registryAccess());
        var manager = server.getRecipeManager();
        if (manager instanceof CandidateRecipeReplay replay) this.registryAccess.bindRecipeDescriptors(replay.nekojs$recipeDescriptors());
        var inline = new InlineRecipeStageRegistry();
        inline.replaceSnapshot(InlineRecipeStageRegistry.SHARED.snapshot(), false);

        StageCustomizeEvent customizeEvent = new StageCustomizeEvent();
        StableStageGatesEvent gatesEvent = new StableStageGatesEvent();
        try {
            StageEvents.CUSTOMIZE.postForCollection(customizeEvent);
            StageEvents.GATES.postForCollection(gatesEvent);
        } catch (Throwable t) {
            NekoJS.LOGGER.error("[NEKO-4071] Stage startup collection dispatch failed; retaining previous gates", t);
            return;
        }

        StageCandidatePlan plan = new StageCandidatePlan(
                null,
                null,
                null,
                activeStore(),
                inline,
                this.registryAccess,
                this::onPlanCommitted
        );
        try {
            plan.addDefinitions(customizeEvent.buildDefinitions());
            plan.addDeclarations(gatesEvent.buildDeclarations());
            plan.addWhitelistedContainers(gatesEvent.whitelistedContainers());
            plan.addWhitelistedContainerSlots(gatesEvent.whitelistedContainerSlots());
            plan.preflight();
            plan.publish();
        } catch (Throwable t) {
            NekoJS.LOGGER.error("[NEKO-4071] Stage plan rejected at server startup (retaining previous gates): {}", t.toString());
        }
    }

    /**
     * Binds the server's world stage directory and registry access, creates and installs the world
     * {@link StageStore}, and applies the initial stage plan.
     */
    public void onServerAboutToStart(MinecraftServer server) {
        bindServer(server);
        if (!worldPlanPublished) applyInitialPlan(server);
    }

    public void bindServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (boundServer == server && store != null) return;
        this.worldPlanPublished = false;
        CURRENT = this;
        this.boundServer = server;
        this.lastSafePositions.clear();
        StageGateEnforcer.clearCooldowns();

        Path stageDir = server.getWorldPath(LevelResource.ROOT).resolve("nekojs").resolve("stages");
        this.registryAccess.bindRegistries(server.registryAccess());

        MinecraftStagePlatformHooks hooks = new MinecraftStagePlatformHooks(
                server,
                StageDomainOwner::activeSnapshot,
                StageDomainOwner::activeStore
        );
        StageStore worldStore = new StageStore(stageDir, hooks);
        BootstrapStagePlan initial = this.bootstrapPlan;
        this.bootstrapPlan = null;
        if (initial != null && initial.manager() == server.getRecipeManager()) {
            worldStore.setDefinitions(initial.definitions());
            this.worldPlanPublished = true;
        }
        this.store = worldStore;
        StageRuntimeBridge.install(worldStore, id -> activeSnapshot().requiredStagesForItem(id));
    }

    /** Flushes all dirty server and player stage data before the server shuts down. */
    public void onServerStopping(MinecraftServer server) {
        StageStore currentStore = this.store;
        if (currentStore != null) {
            try {
                currentStore.saveAll();
            } catch (Throwable t) {
                NekoJS.LOGGER.error("[NEKO-4072] Failed to save stage store while server is stopping", t);
            }
        }
    }

    /** Unbinds server state, flushes any remaining dirty stage files, and resets the runtime bridge. */
    public void onServerStopped() {
        closeResourceAttempts();
        StageStore previousStore = this.store;
        if (previousStore != null) {
            try {
                previousStore.saveAll();
            } catch (Throwable t) {
                NekoJS.LOGGER.error("[NEKO-4072] Failed to flush stage store on server stop", t);
            }
        }
        this.store = null;
        this.boundServer = null;
        this.broadcastGateBaseline = null;
        this.worldPlanPublished = false;
        this.acceptedRecipes = null;
        this.bootstrapPlan = null;
        this.registryAccess.bindRegistries(null);
        this.registryAccess.bindRecipeDescriptors(null);
        InlineRecipeStageRegistry.SHARED.replaceSnapshot(Map.of(), false);
        this.lastSafePositions.clear();
        StageGateEnforcer.clearCooldowns();

        StageRuntimeBridge.uninstall(previousStore);
        this.baseSnapshot = CompiledStageGateSnapshot.compile(
                List.of(),
                Set.of(),
                Map.of(),
                this.registryAccess
        );
        this.snapshot = this.baseSnapshot;
        ACTIVE_SNAPSHOT = CompiledStageGateSnapshot.EMPTY;
    }

    /** Convenience overload accepting the stopped server instance. */
    public void onServerStopped(@Nullable MinecraftServer server) {
        onServerStopped();
    }

    /**
     * Advances server and player temporary stage timers, runs {@code StageDefinition.everyTick()}
     * callbacks (via {@link StageStore#tickServer()}), and enforces periodic inventory, equipment,
     * mob effect, and spatial region gates every 10 ticks.
     */
    public void onServerTickPost(MinecraftServer server) {
        if (server == null) {
            return;
        }
        StageStore currentStore = activeStore();
        // StageStore.tickServer() advances countdowns and dispatches StageDefinition.everyTick() callbacks.
        currentStore.tickServer();

        if (server.getTickCount() % 10 != 0 || server.getPlayerList() == null) {
            return;
        }

        CompiledStageGateSnapshot currentSnapshot = activeSnapshot();
        StageReadView serverView = currentStore.serverHandle();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || !player.isAlive() || StageGateEnforcer.isFakePlayer(player)) {
                continue;
            }
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) {
                continue;
            }
            enforcePeriodicPlayerGates(player, currentSnapshot, serverView);
        }
    }

    /** Loads the joining player's stage bucket and sends a full stage + gate snapshot. */
    public void onPlayerJoin(ServerPlayer player) {
        if (player == null || StageGateEnforcer.isFakePlayer(player)) {
            return;
        }
        StageStore currentStore = activeStore();
        currentStore.onPlayerJoin(player.getStringUUID(), player.getScoreboardName());

        NekoId dimId = StageGateEnforcer.dimensionId(player.level());
        if (dimId != null) {
            StageReadView playerView = ((PlayerExtension) player).neko$getStages();
            StageReadView serverView = currentStore.serverHandle();
            if (activeSnapshot().canEnterRegion(
                    dimId,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    playerView,
                    serverView
            )) {
                lastSafePositions.put(
                        player.getUUID(),
                        new LastSafePos(dimId, player.getX(), player.getY(), player.getZ())
                );
            }
        }
    }

    /** Flushes dirty stage data and clears cached spatial/cooldown state when a player disconnects. */
    public void onPlayerLeave(ServerPlayer player) {
        if (player == null) {
            return;
        }
        lastSafePositions.remove(player.getUUID());
        StageGateEnforcer.clearCooldownsFor(player.getUUID());
        StageStore currentStore = this.store;
        if (currentStore != null && !StageGateEnforcer.isFakePlayer(player)) {
            try {
                currentStore.onPlayerLeave(player.getStringUUID());
            } catch (Throwable t) {
                NekoJS.LOGGER.error(
                        "[NEKO-4072] Failed to save stage data for disconnecting player {}",
                        player.getScoreboardName(),
                        t
                );
            }
        }
    }

    /** Resynchronizes stage and gate state to a newly respawned player entity. */
    public void onPlayerRespawn(ServerPlayer player) {
        if (player == null || StageGateEnforcer.isFakePlayer(player)) {
            return;
        }
        lastSafePositions.remove(player.getUUID());
        activeStore().onPlayerJoin(player.getStringUUID(), player.getScoreboardName());
    }

    /**
     * Re-merges {@link InlineRecipeStageRegistry#SHARED} into the active gate snapshot and
     * broadcasts the updated viewer gate summary to online players.
     */
    public void refreshInlineRecipesAndSync(@Nullable MinecraftServer server, List<com.tkisor.nekojs.core.stage.gate.StageRegistryAccess.RecipeDescriptor> descriptors) {
        this.registryAccess.bindRecipeDescriptors(descriptors);
        refreshInlineRecipesAndSync(server);
    }

    public void refreshInlineRecipesAndSync(@Nullable MinecraftServer server) {
        CompiledStageGateSnapshot merged = this.baseSnapshot.withInlineRecipes(
                InlineRecipeStageRegistry.SHARED.snapshot(), this.registryAccess
        );
        this.snapshot = merged;
        ACTIVE_SNAPSHOT = merged;

        MinecraftServer targetServer = server != null ? server : this.boundServer;
        broadcastGateSummary(targetServer, merged);
    }

    @Override
    public void close() {
        InlineRecipeStageRegistry.SHARED.removeListener(this.inlineRecipeListener);
        onServerStopped();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    private void onPlanCommitted(List<StageDefinition> definitions, CompiledStageGateSnapshot compiled) {
        StageStore currentStore = activeStore();
        currentStore.setDefinitions(definitions);
        installSnapshot(compiled);
        broadcastGateSummary(this.boundServer, compiled);
    }

    private void installSnapshot(CompiledStageGateSnapshot compiled) {
        this.baseSnapshot = compiled;
        this.worldPlanPublished = boundServer != null && store != null;
        this.snapshot = compiled;
        ACTIVE_SNAPSHOT = compiled;
    }

    private void broadcastGateSummary(
            @Nullable MinecraftServer server,
            CompiledStageGateSnapshot snapshot
    ) {
        if (server == null || server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        StageWireProtocol.GateUpdate update = StageWireProtocol.encodeGateUpdate(broadcastGateBaseline, snapshot);
        if (!update.message().isEmpty()) {
            PlayPacketDispatchers.get().sendToAllPlayers(
                    new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, update.message())
            );
        }
        broadcastGateBaseline = update.baseline();
    }

    private void enforcePeriodicPlayerGates(
            ServerPlayer player,
            CompiledStageGateSnapshot currentSnapshot,
            StageReadView serverView
    ) {
        StageReadView playerView = ((PlayerExtension) player).neko$getStages();
        boolean inventoryChanged = false;
        Inventory inventory = player.getInventory();

        // 1a. Enforce equipped armor and offhand slots.
        for (EquipmentSlot slot : GATED_EQUIPMENT_SLOTS) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (equipped.isEmpty()) {
                continue;
            }
            NekoId itemId = StageGateEnforcer.itemId(equipped);
            if (itemId == null) {
                continue;
            }
            boolean canStore = currentSnapshot.canStoreInInventory(itemId, playerView, serverView);
            boolean canEquip = currentSnapshot.canEquipItem(itemId, playerView, serverView);
            if (!canStore) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                player.drop(equipped, false);
                StageGateEnforcer.notifyLockedRule(
                        player,
                        currentSnapshot.lockedItemRule(itemId, playerView, serverView),
                        "item_inv:" + itemId
                );
                inventoryChanged = true;
            } else if (!canEquip) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!inventory.add(equipped) || !equipped.isEmpty()) {
                    player.drop(equipped, false);
                }
                StageGateEnforcer.notifyLockedRule(
                        player,
                        currentSnapshot.lockedItemRule(itemId, playerView, serverView),
                        "item_equip:" + itemId
                );
                inventoryChanged = true;
            }
        }

        // 1b. Enforce main inventory slots (hotbar + main inventory).
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            NekoId itemId = StageGateEnforcer.itemId(stack);
            if (itemId == null) {
                continue;
            }
            if (!currentSnapshot.canStoreInInventory(itemId, playerView, serverView)) {
                inventory.setItem(i, ItemStack.EMPTY);
                player.drop(stack, false);
                StageGateEnforcer.notifyLockedRule(
                        player,
                        currentSnapshot.lockedItemRule(itemId, playerView, serverView),
                        "item_inv:" + itemId
                );
                inventoryChanged = true;
            }
        }

        if (inventoryChanged) {
            player.inventoryMenu.broadcastChanges();
        }

        // 2. Enforce active mob effects.
        Collection<MobEffectInstance> activeEffects = player.getActiveEffects();
        if (!activeEffects.isEmpty()) {
            List<Holder<MobEffect>> disallowedEffects = null;
            for (MobEffectInstance effectInstance : activeEffects) {
                Holder<MobEffect> holder = effectInstance.getEffect();
                NekoId effectId = StageGateEnforcer.resolveEffectId(holder);
                if (effectId != null && !currentSnapshot.canApplyEffect(effectId, playerView, serverView)) {
                    if (disallowedEffects == null) {
                        disallowedEffects = new ArrayList<>();
                    }
                    disallowedEffects.add(holder);
                }
            }
            if (disallowedEffects != null) {
                for (Holder<MobEffect> holder : disallowedEffects) {
                    player.removeEffect(holder);
                }
            }
        }

        rejectRegionMovement(player, player.getX(), player.getY(), player.getZ());
    }

    public static boolean rejectRegionMovement(ServerPlayer player, double x, double y, double z) {
        StageDomainOwner owner = current();
        if (owner == null || !player.isAlive() || StageGateEnforcer.isFakePlayer(player)) return false;
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) return false;
        NekoId dimension = StageGateEnforcer.dimensionId(player.level());
        if (dimension == null) return false;
        CompiledStageGateSnapshot snapshot = activeSnapshot();
        if (!snapshot.hasRegionRules(dimension)) return false;
        StageReadView playerView = ((PlayerExtension) player).neko$getStages();
        StageReadView serverView = activeStore().serverHandle();
        double currentX = player.getX(), currentY = player.getY(), currentZ = player.getZ();
        RegionGate currentRule = snapshot.deniedRegionRule(dimension, currentX, currentY, currentZ,
                playerView, serverView, RegionGate::allowEnter);
        boolean currentAllowed = currentRule == null;
        if (currentAllowed && player.onGround()) {
            owner.lastSafePositions.put(player.getUUID(), new LastSafePos(dimension, currentX, currentY, currentZ));
        }
        RegionGate locked = snapshot.deniedRegionRule(dimension, x, y, z, playerView, serverView, RegionGate::allowEnter);
        if (locked == null) return false;
        StageGateEnforcer.notifyLockedRule(player, locked, "region_enter:" + dimension);
        if (currentAllowed) {
            if (!player.isPassenger() && (x != currentX || y != currentY || z != currentZ)) {
                player.connection.teleport(currentX, currentY, currentZ, player.getYRot(), player.getXRot());
            }
            return true;
        }
        LastSafePos safe = owner.lastSafePositions.get(player.getUUID());
        if (!isSafeRegionPosition(player, dimension, safe, snapshot, playerView, serverView)) {
            safe = findRegionExit(player, dimension, currentRule, snapshot, playerView, serverView);
        }
        if (safe != null) {
            player.stopRiding();
            player.connection.teleport(safe.x(), safe.y(), safe.z(), player.getYRot(), player.getXRot());
            owner.lastSafePositions.put(player.getUUID(), safe);
        }
        return true;
    }

    private static boolean isSafeRegionPosition(ServerPlayer player, NekoId dimension, @Nullable LastSafePos point,
                                                CompiledStageGateSnapshot snapshot, StageReadView playerView,
                                                StageReadView serverView) {
        if (point == null || !dimension.equals(point.dimensionId()) || !snapshot.canEnterRegion(
                dimension, point.x(), point.y(), point.z(), playerView, serverView)) return false;
        var pos = net.minecraft.core.BlockPos.containing(point.x(), point.y(), point.z());
        var level = player.level();
        return level.hasChunkAt(pos) && level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.noCollision(player, player.getBoundingBox().move(
                        point.x() - player.getX(), point.y() - player.getY(), point.z() - player.getZ()))
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private static @Nullable LastSafePos findRegionExit(ServerPlayer player, NekoId dimension, @Nullable RegionGate gate,
                                                       CompiledStageGateSnapshot snapshot, StageReadView playerView,
                                                       StageReadView serverView) {
        if (gate == null) return null;
        LastSafePos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (RegionShape shape : gate.shapes()) {
            if (!shape.contains(dimension, x, y, z)) continue;
            double[][] exits = {{shape.minX() - 1.5, z}, {shape.maxX() + 1.5, z},
                    {x, shape.minZ() - 1.5}, {x, shape.maxZ() + 1.5}};
            for (double[] exit : exits) {
                for (int offset = -4; offset <= 4; offset++) {
                    LastSafePos point = new LastSafePos(dimension, exit[0], Math.floor(y) + offset, exit[1]);
                    double distance = player.distanceToSqr(point.x(), point.y(), point.z());
                    if (distance < nearestDistance && isSafeRegionPosition(player, dimension, point, snapshot, playerView, serverView)) {
                        nearest = point;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private record LastSafePos(NekoId dimensionId, double x, double y, double z) {
    }
}
