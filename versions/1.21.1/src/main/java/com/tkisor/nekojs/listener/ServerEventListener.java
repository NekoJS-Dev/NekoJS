// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.definition.MinecraftRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeSchemaAutoDiscovery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.resource.RecipeTypeDefinitionResources;
import com.tkisor.nekojs.resource.ScriptPackDataManager;
import com.tkisor.nekojs.stage.StageDomainOwner;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Handles server lifecycle, reload, and tag-update events on 1.21.1. */
@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    private static volatile boolean schemaAutoDiscovered;

    /** Narrow lifecycle handle injected by bind() once the loader entry is assembled; always set before
     * any server event fires. */
    private static volatile NekoRuntimeRoot runtimeRoot;

    /** Root-owned stage domain collector bound during mod initialization. */
    private static volatile StageDomainOwner boundStageDomainOwner;

    /** Called by {@code NekoJSMod} after the root is assembled; always before any server or world
     * event. */
    public static void bind(NekoRuntimeRoot root) {
        runtimeRoot = root;
    }

    /** Injects the root-owned {@link StageDomainOwner} created during mod initialization. */
    public static void bindStageDomain(StageDomainOwner stageDomainOwner) {
        boundStageDomainOwner = stageDomainOwner;
    }

    private static StageDomainOwner stageDomain() {
        StageDomainOwner owner = boundStageDomainOwner;
        if (owner != null) {
            return owner;
        }
        var root = runtimeRoot;
        if (root != null) {
            var collector = root.domainCollector(StageDomainOwner.DOMAIN);
            if (collector instanceof StageDomainOwner rootOwner) {
                return rootOwner;
            }
        }
        return StageDomainOwner.current();
    }

    static {
        // Applies script changes from the lootTables event while loot tables load (part of the reload flow).
        NeoForge.EVENT_BUS.addListener(LootTableEventJS::onLootTableLoad);
    }

    /**
     * Startup probe, enabled by {@code runAtStartup = true} in probe.toml (off by default): runs the
     * default TS backend once the server has fully started and writes a summary of the results to the
     * log. A failure does not affect server startup.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var root = runtimeRoot;
        if (root != null) ProbeCoordinator.runStartupProbeIfConfigured(root.probeBackends());
    }

    /** Activates world packs, mounts script data, and applies initial modification, trade, and stage plans before server start. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        var server = event.getServer();
        StageDomainOwner stageDomainOwner = stageDomain();
        if (stageDomainOwner != null) stageDomainOwner.bindServer(server);
        activateWorldPacks(server);
        mountScriptPackData(server);
        // Active item listeners collect the initial generation plan; the domain owner preflights
        // the full batch before restoring baselines and applying changes. Transactional SERVER
        // reload replays this plan in DOMAIN_PLAN. The block property/modification surface is
        // absent on 1.21.1, as is dynamic registration; node re-extraction must preserve both gaps.
        com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner modificationDomain = modificationDomain();
        if (modificationDomain != null) {
            modificationDomain.applyInitialPlan(server);
        }
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTradeDomain = villagerTradeDomain();
        if (villagerTradeDomain != null) {
            villagerTradeDomain.applyInitialPlan(server);
        }
        if (stageDomainOwner != null) {
            stageDomainOwner.onServerAboutToStart(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        StageDomainOwner stageDomainOwner = stageDomain();
        if (stageDomainOwner != null) {
            stageDomainOwner.onServerStopping(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        StageDomainOwner stageDomainOwner = stageDomain();
        if (stageDomainOwner != null) {
            stageDomainOwner.onServerTickPost(event.getServer());
        }
    }

    /** Modification domain owner from the domain collector the root provides; returns {@code null} when
     * no owner is registered, and the startup collection step then skips it. */
    private static com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner modificationDomain() {
        com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector =
                runtimeRoot == null ? null
                        : runtimeRoot.domainCollector(com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner.DOMAIN);
        return collector instanceof com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner
                ? owner : null;
    }

    /**
     * Villager Trades owner (root-authorised domain collector; absent on unsupported loader nodes).
     */
    private static com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTradeDomain() {
        com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector =
                runtimeRoot == null ? null
                        : runtimeRoot.domainCollector(com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner.DOMAIN);
        return collector instanceof com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner owner
                ? owner : null;
    }

    /**
     * Costs nothing while the content signature is unchanged. When it changed, runs repository.reload,
     * setSelected and server.reloadResources on the server thread, then removes the nekojs pack id
     * from the worldData config (without writing it to disk).
     */
    private static void mountScriptPackData(net.minecraft.server.MinecraftServer server) {
        try {
            var packs = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().enabledPacks();
            if (!ScriptPackDataManager.hasDataPacks(packs)) return;
            boolean changed = ScriptPackDataManager.activateForServer(server, packs);
            if (changed) {
                ScriptPackDataManager.reloadServerResources(server);
            }
        } catch (Exception e) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Failed to mount script pack data packs: ", e);
        }
    }

    /**
     * Activates the world script packs stored in the save ({@code <world>/nekojs_packs/}). The regular
     * SERVER reload runs during resource loading ({@link #onServerResourceReload}), before this event,
     * so a full SERVER reload runs here to pick up the pack scripts when any world pack was activated.
     * With no world packs it costs nothing.
     */
    private static void activateWorldPacks(net.minecraft.server.MinecraftServer server) {
        var worldPacks = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get()
                .activateWorldPacks(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        if (worldPacks.isEmpty()) return;
        try {

            runtimeRoot.reload(ScriptType.SERVER);
        } catch (Exception e) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("SERVER reload after world pack activation failed: ", e);
        }
    }

    /**
     * Unloads the world script packs: unregisters SERVER listeners and timers by pack prefix. In
     * single-player the CLIENT side is cleaned up as well; client scripts reload as a whole on the next
     * world join, so clearing only the in-memory listeners here stops world pack callbacks from firing
     * on menu ticks after the player returns to the title screen.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        var removed = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().deactivateWorldPacks();
        // The script pack datapack mount and the villager trade snapshot die with the server instance;
        // reset them so the next mount starts clean.
        ScriptPackDataManager.reset();

        com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner modificationDomain = modificationDomain();
        if (modificationDomain != null) {
            modificationDomain.clearServer();
        }
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTradeDomain = villagerTradeDomain();
        if (villagerTradeDomain != null) {
            villagerTradeDomain.clearServer();
        }
        StageDomainOwner stageDomainOwner = stageDomain();
        if (stageDomainOwner != null) {
            stageDomainOwner.onServerStopped();
        }
        if (runtimeRoot != null) runtimeRoot.mixins().clearCallbacks();
        if (runtimeRoot == null || removed.isEmpty()) return;
        runtimeRoot.clearWorldPackListeners(ScriptType.SERVER, removed);
        if (com.tkisor.nekojs.platform.Platform.isClient()) {
            runtimeRoot.clearWorldPackListeners(ScriptType.CLIENT, removed);
        }
    }

    /** Registers NekoJS script reload listeners with the server resource reload pipeline. */
    @SubscribeEvent
    public static void onServerResourceReload(AddReloadListenerEvent event) {
        if (!schemaAutoDiscovered) {
            try {
                RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered = MinecraftRecipeSchemaScanner.scan();
                RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeSchemaAutoDiscovery.discover(() -> discovered));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to auto-discover recipe schemas: {}", e.getMessage());
            }
            schemaAutoDiscovered = true;
        }
        event.addListener((ResourceManagerReloadListener) manager ->
                RecipeTypeDefinitionStorage.replace(RecipeTypeDefinitionResources.load(manager)));
        // Loot table JSON handling: the reload flow runs before loot parsing, so a change applies to that reload.
        var owner = stageDomain();
        if (owner != null) {
            owner.dispatchResourceScripts(event.getServerResources(), ServerEventListener::dispatchResourceScripts);
        } else {
            dispatchResourceScripts(null);
        }
    }

    private static void dispatchResourceScripts(
            com.tkisor.nekojs.core.pack.PackActivationCoordinator.PreparedReload pending) {
        if (pending == null) ServerEvents.LOOT_TABLES.post(new LootTableEventJS());
        else pending.dispatch(ServerEvents.LOOT_TABLES, new LootTableEventJS(), null);
        postGenerateData(pending);
    }

    /** Replays villager trade modifications after server data tags finish reloading. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onServerDataTagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
        if (event.getUpdateCause() != net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        var owner = villagerTradeDomain();
        if (owner != null) owner.replayAfterResourceReload();
    }

    /**
     * Data generation event: scripts and plugins write datapack JSON to {@code <gameDir>/nekojs/data},
     * a datapack on disk read lazily so the reload order stays correct. Only one stage is supported
     * today, {@code after_mods}.
     *
     * <p>The plugin {@code generateData} hook and the script {@code ServerEvents.generateData} event
     * both go through {@link PluginGenerationHooks#runGenerateData}: candidates are collected,
     * validated and published atomically, so neither writes the active root directly. A failed publish
     * keeps the previous data; see {@code DataGenerationBatch}.
     */
    private static void postGenerateData(com.tkisor.nekojs.core.pack.PackActivationCoordinator.PreparedReload pending) {
        try {
            com.tkisor.nekojs.wrapper.DataGenerationBatch.PublishResult result =
                    PluginGenerationHooks.runGenerateData(NekoJSPaths.get().data(), "after_mods",
                            generator -> {
                                if (pending == null) ServerEvents.GENERATE_DATA.post(generator, "after_mods");
                                else pending.dispatch(ServerEvents.GENERATE_DATA, generator, "after_mods");
                            });
            int skippedCount = result.skippedUserFiles().size();
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).info(
                    "generateData stage 'after_mods' published {} file(s); skipped {} user-owned file(s)",
                    result.published().size(), skippedCount);
            if (skippedCount > 0) {
                Object skippedSummary = skippedCount > 10
                        ? result.skippedUserFiles().subList(0, 10) + " +" + (skippedCount - 10) + " more"
                        : result.skippedUserFiles();
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).warn(
                        "generateData stage 'after_mods' skipped user-owned files (never overwritten): {}",
                        skippedSummary);
            }
        } catch (Exception e) {
            if (pending != null) throw new IllegalStateException("[NEKO-4071] Data generation rejected the resource script candidate", e);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER)
                    .error("generateData batch failed; nothing was published and the previous active data is retained: ", e);
        }
    }
}
