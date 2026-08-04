package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableLoadEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;
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

import net.minecraft.resources.ResourceLocation;

public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    DispatchKey<TagEventJS, ResourceLocation> TAG_REGISTRY_KEY = new DispatchKey<>() {
        @Override
        public Class<ResourceLocation> keyType() {
            return ResourceLocation.class;
        }

        @Override
        public ResourceLocation eventToKey(TagEventJS event) {
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

    // 统一 wrapper：tickPre/tickPost → ServerTickEventJS（非分发）
    EventBusJS<ServerTickEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", ServerTickEventJS.class);
    EventBusJS<ServerTickEventJS, Void> TICK_POST =
            GROUP.server("tickPost", ServerTickEventJS.class);

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /** 数据生成事件：脚本写入 datapack JSON（loot tables / advancements / worldgen 等）。 */
    EventBusJS<DataGeneratorJS, String> GENERATE_DATA =
            GROUP.server("generateData", DataGeneratorJS.class, STAGE_KEY);

    // 统一 wrapper：服务器生命周期 → ServerLifecycleEventJS（非分发）
    EventBusJS<ServerLifecycleEventJS, Void> ABOUT_TO_START =
        GROUP.server("aboutToStart", ServerLifecycleEventJS.class);
    EventBusJS<ServerLifecycleEventJS, Void> STARTING =
        GROUP.server("starting", ServerLifecycleEventJS.class);
    EventBusJS<ServerLifecycleEventJS, Void> STARTED =
        GROUP.server("started", ServerLifecycleEventJS.class);
    EventBusJS<ServerLifecycleEventJS, Void> STOPPING =
        GROUP.server("stopping", ServerLifecycleEventJS.class);
    EventBusJS<ServerLifecycleEventJS, Void> STOPPED =
        GROUP.server("stopped", ServerLifecycleEventJS.class);

    // 以下保持原生事件类型（已有专属 wrapper 或仅限特定平台，按约定不统一）
    EventBusJS<OnDatapackSyncEvent, Void> DATAPACK_SYNC =
        GROUP.server("datapackSync", OnDatapackSyncEvent.class);
    EventBusJS<TagsUpdatedEvent, Void> TAGS_UPDATED =
        GROUP.server("tagsUpdated", TagsUpdatedEvent.class);

    // 统一 wrapper：lootTableLoad → LootTableLoadEventJS（非分发）
    EventBusJS<LootTableLoadEventJS, Void> LOOT_TABLE_LOAD =
        GROUP.server("lootTableLoad", LootTableLoadEventJS.class);

    /** loot table JSON 管理（reload 时 post，先于 loot 解析；修改当次 reload 生效）。 */
    EventBusJS<LootTableEventJS, Void> LOOT_TABLES =
        GROUP.server("lootTables", LootTableEventJS.class);

    EventBusJS<TagEventJS, ResourceLocation> TAGS =
        GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY);

    // —— NeoForge ServerTickEvent/ServerLifecycleEvent/LootTableLoadEvent → wrapper transformer ——
    private static ServerTickEventJS fromServerTick(ServerTickEvent event) {
        return new ServerTickEventJS(event.getServer(), event.hasTime());
    }

    private static ServerLifecycleEventJS fromLifecycle(
            net.neoforged.neoforge.event.server.ServerLifecycleEvent event) {
        return new ServerLifecycleEventJS(event.getServer());
    }

    private static LootTableLoadEventJS fromLootTableLoad(LootTableLoadEvent event) {
        return new LootTableLoadEventJS(event.getName().toString(), event.getTable());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bindTransformed(TICK_PRE, ServerEvents::fromServerTick, ServerTickEvent.Pre.class)
        .bindTransformed(TICK_POST, ServerEvents::fromServerTick, ServerTickEvent.Post.class)
        .bindTransformed(ABOUT_TO_START, ServerEvents::fromLifecycle, ServerAboutToStartEvent.class)
        .bindTransformed(STARTING, ServerEvents::fromLifecycle, ServerStartingEvent.class)
        .bindTransformed(STARTED, ServerEvents::fromLifecycle, ServerStartedEvent.class)
        .bindTransformed(STOPPING, ServerEvents::fromLifecycle, ServerStoppingEvent.class)
        .bindTransformed(STOPPED, ServerEvents::fromLifecycle, ServerStoppedEvent.class)
        .bind(DATAPACK_SYNC)
        .bind(TAGS_UPDATED)
        .bindTransformed(LOOT_TABLE_LOAD, ServerEvents::fromLootTableLoad, LootTableLoadEvent.class);
}
