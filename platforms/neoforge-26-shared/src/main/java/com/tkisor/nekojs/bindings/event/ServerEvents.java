package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
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
    EventGroup GROUP = EventGroup.of("ServerEvents");

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

    /** 数据生成阶段 key：脚本以 {@code ServerEvents.generateData('after_mods', ...)} 定向。 */
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

    EventBusJS<ServerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", ServerTickEvent.Pre.class);
    EventBusJS<ServerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", ServerTickEvent.Post.class);

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /** 数据生成事件：脚本写入 datapack JSON（loot tables / advancements / worldgen 等）。 */
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

    /** loot table JSON 管理（reload 时 post，先于 loot 解析；修改当次 reload 生效）。 */
    EventBusJS<LootTableEventJS, Void> LOOT_TABLES =
        GROUP.server("lootTables", LootTableEventJS.class);

    EventBusJS<TagEventJS, Identifier> TAGS =
        GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY);

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
