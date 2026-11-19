//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableDatapackSyncEvent;
import com.tkisor.nekojs.api.event.StableLootTableLoadEvent;
import com.tkisor.nekojs.api.event.StableServerLifecycleEvent;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.api.event.StableTagsUpdatedEvent;
import com.tkisor.nekojs.api.recipe.StableRecipeEvent;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.stage.StableRecipeEventProjection;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Server event group for server scripts: server lifecycle, ticks, recipes, tags, loot tables,
 * datapack generation, and villager trades.
 */
@Doc("Server event group for server scripts: lifecycle, ticks, recipes, tags, loot tables, datapack generation, and villager trades.")
public interface ServerEvents {
    static boolean hasStableLootTableLoadListeners() {
        return LOOT_TABLE_LOAD.hasStableModuleListeners(ScriptType.SERVER);
    }

    EventGroup GROUP = EventGroup.of("ServerEvents");

    /** Dispatches tag events by registry id (for example {@code minecraft:item}). */
    DispatchKey<TagEventJS, Identifier> TAG_REGISTRY_KEY = new DispatchKey<>() {
        @Override
        public Class<Identifier> keyType() {
            return Identifier.class;
        }

        @Override
        public Identifier eventToKey(TagEventJS event) {
            return event.getRegistry();
        }
    };

    /**
     * Data generation stage key: scripts dispatch on it with
     * {@code ServerEvents.generateData('after_mods', ...)}.
     */
    DispatchKey<DataGeneratorJS, String> STAGE_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(DataGeneratorJS event) {
            return event.getStage();
        }
    };

    // Both versions declare this identically (ServerTickEvent / RecipeEventJS have the same package
    // and name on each side), so no guard is needed.
    /** Fired at the start of each server tick. */
    @Doc("Fired at the start of each server tick.")
    EventBusJS<ServerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", ServerTickEvent.Pre.class, api -> {
                api.versioned();
                api.stable().when(event -> event.getServer().isSameThread())
                        .view(StableServerTickEvent.class,
                        event -> new StableServerTickEvent(event.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired at the end of each server tick. */
    @Doc("Fired at the end of each server tick.")
    EventBusJS<ServerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", ServerTickEvent.Post.class, api -> {
                api.versioned();
                api.stable().when(event -> event.getServer().isSameThread())
                        .view(StableServerTickEvent.class,
                        event -> new StableServerTickEvent(event.getServer().getTickCount()))
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired during datapack reload to add, remove, or modify recipes. */
    @Doc("Fired during datapack reload to add, remove, or modify recipes.")
    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class, api -> {
        api.versioned();
        api.stable().view(StableRecipeEvent.class, StableRecipeEventProjection::new)
                .scriptTypes(ScriptType.SERVER);
    });

    /** Fired after {@link #RECIPES} completes during datapack reload. */
    @Doc("Fired after recipe modifications complete during datapack reload.")
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class, api -> api.versioned());

    /** Data generation event: scripts write datapack JSON (loot tables, advancements, worldgen, and so on). */
    @Doc("Data generation event for writing virtual datapack JSON (loot tables, advancements, worldgen, etc.). Dispatches by stage.")
    EventBusJS<DataGeneratorJS, String> GENERATE_DATA =
            GROUP.server("generateData", DataGeneratorJS.class, STAGE_KEY, api -> api.versioned());

    /** Fired right before the server begins starting. */
    @Doc("Fired right before the server begins starting.")
    EventBusJS<ServerAboutToStartEvent, Void> ABOUT_TO_START =
        GROUP.server("aboutToStart", ServerAboutToStartEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> event.getServer().isSameThread())
                    .view(StableServerLifecycleEvent.class,
                    e -> NeoForgeStableEventProjections.serverLifecycle("about_to_start", e))
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired while the server is starting. */
    @Doc("Fired while the server is starting.")
    EventBusJS<ServerStartingEvent, Void> STARTING =
        GROUP.server("starting", ServerStartingEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> event.getServer().isSameThread())
                    .view(StableServerLifecycleEvent.class,
                    e -> NeoForgeStableEventProjections.serverLifecycle("starting", e))
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired once the server has finished starting and is ready for players. */
    @Doc("Fired once the server has finished starting.")
    EventBusJS<ServerStartedEvent, Void> STARTED =
        GROUP.server("started", ServerStartedEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> event.getServer().isSameThread())
                    .view(StableServerLifecycleEvent.class,
                    e -> NeoForgeStableEventProjections.serverLifecycle("started", e))
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired when the server begins shutting down. */
    @Doc("Fired when the server begins shutting down.")
    EventBusJS<ServerStoppingEvent, Void> STOPPING =
        GROUP.server("stopping", ServerStoppingEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> event.getServer().isSameThread())
                    .view(StableServerLifecycleEvent.class,
                    e -> NeoForgeStableEventProjections.serverLifecycle("stopping", e))
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired after the server has completely stopped. */
    @Doc("Fired after the server has completely stopped.")
    EventBusJS<ServerStoppedEvent, Void> STOPPED =
        GROUP.server("stopped", ServerStoppedEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> event.getServer().isSameThread())
                    .view(StableServerLifecycleEvent.class,
                    e -> NeoForgeStableEventProjections.serverLifecycle("stopped", e))
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired when datapack data is synchronized to players. */
    @Doc("Fired when datapack data is synchronized to players.")
    EventBusJS<OnDatapackSyncEvent, Void> DATAPACK_SYNC =
        GROUP.server("datapackSync", OnDatapackSyncEvent.class, api -> {
            api.versioned();
            api.stable()
                    .when(event -> event.getPlayerList() != null && event.getPlayerList().getServer().isSameThread())
                    .view(StableDatapackSyncEvent.class, NeoForgeStableEventProjections::datapackSync)
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired after registry tags are updated on the server. */
    @Doc("Fired after registry tags are updated on the server.")
    EventBusJS<TagsUpdatedEvent, Void> TAGS_UPDATED =
        GROUP.server("tagsUpdated", TagsUpdatedEvent.class, api -> {
            api.versioned();
            api.stable()
                    .when(event -> {
                        var server = ServerLifecycleHooks.getCurrentServer();
                        return event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD
                                && server != null && server.isSameThread();
                    })
                    .view(StableTagsUpdatedEvent.class, NeoForgeStableEventProjections::tagsUpdated)
                    .scriptTypes(ScriptType.SERVER);
        });

    /** Fired when an individual loot table is loaded from datapack resources. */
    @Doc("Fired when an individual loot table is loaded from datapack resources.")
    EventBusJS<LootTableLoadEvent, Void> LOOT_TABLE_LOAD =
        GROUP.server("lootTableLoad", LootTableLoadEvent.class, api -> {
            api.versioned();
            api.stable().when(event -> ServerLootReloadExecutor.isStableDispatch())
                    .view(StableLootTableLoadEvent.class,
                    NeoForgeStableEventProjections::lootTableLoad)
                    .cancellable()
                    .scriptTypes(ScriptType.SERVER);
        });

    /**
     * Loot table JSON management: posted during reload, before loot parsing, and the changes
     * apply to that reload.
     */
    @Doc("Loot table JSON management event posted during reload before loot parsing.")
    EventBusJS<LootTableEventJS, Void> LOOT_TABLES =
        GROUP.server("lootTables", LootTableEventJS.class, api -> api.versioned());

    /** Fired during reload to add or remove registry tag entries; dispatches by registry id. */
    @Doc("Fired during reload to modify registry tags; dispatches by registry id (e.g. 'minecraft:item').")
    EventBusJS<TagEventJS, Identifier> TAGS =
        GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY, api -> api.versioned());

    /**
     * Villager and wandering trader trade declarations (server scripts). Posted at server start
     * and during a server script reload; callbacks describe trades and registry updates are
     * applied when the reload commits.
     */
    @Doc("Declares villager and wandering trader trades at server start and on server script reload.")
    EventBusJS<VillagerTradeDeclarationEventJS, Void> TRADE_DECLARATION =
        GROUP.server("tradeDeclaration", VillagerTradeDeclarationEventJS.class, api -> api.versioned());

    /**
     * Villager trade reload sub-event: a script that owns a trade set declares that it no longer
     * manages it ({@code declareObsolete}); the release takes effect in the same commit. An
     * ordinary reload never physically deletes trades that are simply no longer declared — those
     * go into the stale/unrestored record instead.
     */
    @Doc("Villager trade reload event for explicitly retiring previously declared trade sets via declareObsolete.")
    EventBusJS<VillagerTradeReloadEventJS, Void> TRADE_RELOAD =
        GROUP.server("tradeReload", VillagerTradeReloadEventJS.class, api -> api.versioned());

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bind(TICK_PRE)
        .bind(TICK_POST)
        .bind(ABOUT_TO_START)
        .bind(STARTING)
        .bind(STARTED)
        .bind(STOPPING)
        .bind(STOPPED)
        .bind(DATAPACK_SYNC)
        .bind(TAGS_UPDATED)
        .bind(LOOT_TABLE_LOAD);
}
//?}
