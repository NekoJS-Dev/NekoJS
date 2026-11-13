//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
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
import net.minecraft.resources.Identifier;

public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents").expose(api -> api.versioned());

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

    /** Data generation stage key: scripts dispatch on it with
     * {@code ServerEvents.generateData('after_mods', ...)}. */
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
    EventBusJS<ServerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", ServerTickEvent.Pre.class, api ->
                    api.stable(StableServerTickEvent.class,
                            event -> new StableServerTickEvent(event.getServer().getTickCount()))
                            .scriptTypes(ScriptType.SERVER));
    EventBusJS<ServerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", ServerTickEvent.Post.class, api ->
                    api.stable(StableServerTickEvent.class,
                            event -> new StableServerTickEvent(event.getServer().getTickCount()))
                            .scriptTypes(ScriptType.SERVER));

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /** Data generation event: scripts write datapack JSON (loot tables, advancements, worldgen and so on). */
    EventBusJS<DataGeneratorJS, String> GENERATE_DATA =
            GROUP.server("generateData", DataGeneratorJS.class, STAGE_KEY);

    EventBusJS<ServerAboutToStartEvent, Void> ABOUT_TO_START =
        GROUP.server("aboutToStart", ServerAboutToStartEvent.class);
    EventBusJS<ServerStartingEvent, Void> STARTING =
        GROUP.server("starting", ServerStartingEvent.class);
    EventBusJS<ServerStartedEvent, Void> STARTED =
        GROUP.server("started", ServerStartedEvent.class);
    EventBusJS<ServerStoppingEvent, Void> STOPPING =
        GROUP.server("stopping", ServerStoppingEvent.class);
    EventBusJS<ServerStoppedEvent, Void> STOPPED =
        GROUP.server("stopped", ServerStoppedEvent.class);
    EventBusJS<OnDatapackSyncEvent, Void> DATAPACK_SYNC =
        GROUP.server("datapackSync", OnDatapackSyncEvent.class);
    EventBusJS<TagsUpdatedEvent, Void> TAGS_UPDATED =
        GROUP.server("tagsUpdated", TagsUpdatedEvent.class);
    EventBusJS<LootTableLoadEvent, Void> LOOT_TABLE_LOAD =
        GROUP.server("lootTableLoad", LootTableLoadEvent.class);

    /** Loot table JSON management: posted during reload, before loot parsing, and the changes
     * apply to that reload. */
    EventBusJS<LootTableEventJS, Void> LOOT_TABLES =
        GROUP.server("lootTables", LootTableEventJS.class);

    EventBusJS<TagEventJS, Identifier> TAGS =
        GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY);

    /**
     * Villager and wandering trader trade declarations (server scripts, posted-object mode): the
     * platform domain adapter ({@code VillagerTradeDomainOwner}) posts this at the server-start
     * collection point and in the candidate {@code DOMAIN_PLAN} phase of a SERVER reload.
     * Callbacks describe trades; the registry mutation happens only at the legal commit point of
     * the platform/version adapter. The first version exposes {@code add} only.
     */
    EventBusJS<VillagerTradeDeclarationEventJS, Void> TRADE_DECLARATION =
        GROUP.server("tradeDeclaration", VillagerTradeDeclarationEventJS.class);

    /**
     * Villager trade reload sub-event: a script that owns a trade set declares that it no longer
     * manages it ({@code declareObsolete}); the release takes effect in the same commit. An
     * ordinary reload never physically deletes trades that are simply no longer declared — those
     * go into the stale/unrestored record instead.
     */
    EventBusJS<VillagerTradeReloadEventJS, Void> TRADE_RELOAD =
        GROUP.server("tradeReload", VillagerTradeReloadEventJS.class);

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
