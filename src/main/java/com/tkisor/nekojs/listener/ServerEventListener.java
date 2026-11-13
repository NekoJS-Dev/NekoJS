// 26.x implementation. This file must not carry version guards again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; keep it in sync when you change this file.
//? if neoforge {
package com.tkisor.nekojs.listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.definition.MinecraftRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeSchemaAutoDiscovery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionJsonLoader;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.resource.ScriptPackDataManager;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.io.Reader;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    private static volatile boolean schemaAutoDiscovered;

    /** Narrow lifecycle handle injected through bind once the loader entry is assembled; it is always
     * ready by the time a server event fires. */
    private static volatile NekoRuntimeRoot runtimeRoot;

    /** Called by {@code NekoJSMod} after the root is assembled; it runs before any server or world event. */
    public static void bind(NekoRuntimeRoot root) {
        runtimeRoot = root;
    }

    static {
        // Applies the script changes made in the lootTables event when loot tables load during a reload.
        NeoForge.EVENT_BUS.addListener(LootTableEventJS::onLootTableLoad);
    }

    /**
     * Startup probe. It is enabled when {@code probe.toml} sets {@code runAtStartup = true} and is off
     * by default. Once the server has fully started it runs the default TS backend once and logs a
     * summary of the result; a failure does not affect server startup.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ProbeCoordinator.runStartupProbeIfConfigured();
    }

//? if >=26 {
    @SubscribeEvent
    public static void onModificationPlayerLoggedIn(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner owner = modificationDomain();
            if (owner != null) owner.syncTo(player);
        }
    }
//?}
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        var server = event.getServer();
        activateWorldPacks(server);
        mountScriptPackData(server);
        // Item and Block property changes, collection point for the initial generation: the event goes to
        // the active bus listeners, and once the domain owner's preflight passes, the baseline is
        // restored and the full plan applied (a failed preflight leaves the whole batch blocked at its
        // current values). The SERVER transaction reload of `/nekojs reload server` and of vanilla
        // /reload collects through pending candidate listeners in the DOMAIN_PLAN stage and applies
        // them together at the commit point, under the same owner. The client does not resync on its
        // own: lighting and other visual changes appear only after the player rejoins the world or the
        // chunk resyncs.
        com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner modificationDomain = modificationDomain();
        if (modificationDomain != null) {
            modificationDomain.applyInitialPlan(server);
        }
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTradeDomain = villagerTradeDomain();
        if (villagerTradeDomain != null) {
            villagerTradeDomain.applyInitialPlan(server);
        }
        // Collect the first inert dynamic batch after server registries become available.
        // The guard preserves the 1.21.1 node's deliberate absence when re-extracting its source.
//? if >=26 {
        com.tkisor.nekojs.dynamic.DynamicRegistryFacade.fireInitialCollection();
//?}
    }

    /** The modification domain owner. The root grants the domain collector; returns null when none is
     * registered, in which case the startup collection point is skipped. */
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
     * Mounts the {@code data/} directory of script packs as a synthetic server datapack; only enabled
     * packs that contain data/ take part. Costs nothing when the content signature has not changed.
     * When it has changed, runs repository.reload, setSelected and server.reloadResources on the
     * server thread, then removes the nekojs pack id from the worldData config without writing it to
     * disk.
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
     * Activates the world script packs stored under {@code <world>/nekojs_packs/}. A regular SERVER
     * reload happens while resources load ({@link #onServerResourceReload}), which is before this
     * event, so when world packs were activated, one more full SERVER reload brings their scripts in.
     * With no world packs the call costs nothing.
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
     * Unloads the world script packs: unregisters the SERVER listeners and timers by pack prefix. In a
     * single-player session the CLIENT side is cleaned up the same way, because client scripts reload
     * as a whole on the next world join and this only clears listeners from memory, so world pack
     * callbacks stop firing on the menu tick after the player returns to the title screen.
     */
    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        var removed = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().deactivateWorldPacks();
        // The script pack datapack mount and the villager trade snapshot die with the server instance; reset
        // them so the next mount starts clean.
        ScriptPackDataManager.reset();

        // The server binding of the modification domain dies with the server instance; the baseline is the
        // one process-wide exception and lives until the root closes.
        com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner modificationDomain = modificationDomain();
        if (modificationDomain != null) {
            modificationDomain.clearServer();
        }
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTradeDomain = villagerTradeDomain();
        if (villagerTradeDomain != null) {
            villagerTradeDomain.clearServer();
        }
        if (runtimeRoot != null) runtimeRoot.mixins().clearCallbacks();
        if (removed.isEmpty()) return;
        runtimeRoot.clearWorldPackListeners(ScriptType.SERVER, removed);
        if (com.tkisor.nekojs.platform.Platform.isClient()) {
            runtimeRoot.clearWorldPackListeners(ScriptType.CLIENT, removed);
        }
    }

    /** Replays accepted trade declarations after resource reload installs fresh registry instances. */
    @SubscribeEvent
    public static void onServerDataTagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad event) {
        var owner = villagerTradeDomain();
        if (owner != null) {
            owner.replayAfterResourceReload();
        }
    }

    @SubscribeEvent
    public static void onServerResourceReload(AddServerReloadListenersEvent event) {
        if (!schemaAutoDiscovered) {
            try {
                RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered = MinecraftRecipeSchemaScanner.scan();
                RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeSchemaAutoDiscovery.discover(() -> discovered));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to auto-discover recipe schemas: {}", e.getMessage());
            }
            schemaAutoDiscovered = true;
        }
        event.addListener(Identifier.fromNamespaceAndPath(NekoJS.MODID, "recipe_type_definitions"), (ResourceManagerReloadListener) ServerEventListener::loadRecipeTypeDefinitions);
        try {

            runtimeRoot.reload(ScriptType.SERVER);
        } catch (Exception e) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Script overload failed: ", e);
        }
        // Loot table JSON handling; the reload flow runs before loot parsing, so changes apply to this reload.
        ServerEvents.LOOT_TABLES.post(new LootTableEventJS());
        postGenerateData();
    }

    /**
     * Data generation event. Scripts and plugins write datapack JSON into
     * {@code <gameDir>/nekojs/data}, a datapack on disk that is read lazily so reload ordering stays
     * correct. Only the single {@code after_mods} stage is supported.
     *
     * <p>The plugin {@code generateData} hook and the script event {@code ServerEvents.generateData}
     * both go through {@link PluginGenerationHooks#runGenerateData}, which collects candidates,
     * validates them and publishes them atomically, instead of each writing to the active root
     * directly. A failed publish keeps the previous data; see {@code DataGenerationBatch}.
     */
    private static void postGenerateData() {
        try {
            com.tkisor.nekojs.wrapper.DataGenerationBatch.PublishResult result =
                    PluginGenerationHooks.runGenerateData(NekoJSPaths.get().data(), "after_mods",
                            generator -> ServerEvents.GENERATE_DATA.post(generator, "after_mods"));
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
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER)
                    .error("generateData batch failed; nothing was published and the previous active data is retained: ", e);
        }
    }

    private static void loadRecipeTypeDefinitions(ResourceManager manager) {
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();
        for (Map.Entry<Identifier, Resource> entry : manager.listResources("nekojs/recipe_types", path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier resourceId = entry.getKey();
            String path = resourceId.getPath();
            String prefix = "nekojs/recipe_types/";
            String typeName = path.substring(prefix.length(), path.length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!json.isJsonObject()) {
                    throw new IllegalArgumentException("Recipe type definition must be an object");
                }
                builder.add(RecipeTypeDefinitionJsonLoader.parse(resourceId.getNamespace(), typeName, json.getAsJsonObject()));
            } catch (Exception e) {
                NekoJS.LOGGER.error("Failed to load recipe type definition {}", resourceId, e);
            }
        }
        RecipeTypeDefinitionStorage.replace(builder.build());
    }
}
//?}
