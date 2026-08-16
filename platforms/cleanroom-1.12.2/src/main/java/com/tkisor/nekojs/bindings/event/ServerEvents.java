package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * 服务器相关事件总线声明：服务器 tick、生命周期、配方/tag/数据生成与 loot table 事件。
 */
public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    // 1.12.2 tick 事件为单一类带 phase 字段：tickPre/tickPost 用 filter 拆分，避免双触发
    EventBusJS<TickEvent.ServerTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.ServerTickEvent.class);
    EventBusJS<TickEvent.ServerTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.ServerTickEvent.class);

    // worldLoad/worldUnload 与 LevelEvents.loaded/unloaded 同语义双入口（历史遗留）。
    // 脚本侧建议迁移到 LevelEvents.loaded/unloaded（跨平台名；H-5 别名裁决，2026-08-15）。
    @Deprecated
    EventBusJS<WorldEvent.Load, Void> WORLD_LOAD =
            GROUP.server("worldLoad", WorldEvent.Load.class);
    @Deprecated
    EventBusJS<WorldEvent.Unload, Void> WORLD_UNLOAD =
            GROUP.server("worldUnload", WorldEvent.Unload.class);

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /**
     * 1.12.2 tag 事件（OreDictionary 适配）。dispatch 键固定 {@code "ore_dict"}
     * （1.12.2 无按注册表分类的现代 tag）。脚本以 {@code ServerEvents.tags('ore_dict', e => ...)} 监听。
     * <p>语义边界：仅物品/方块；移除是运行时操作；非 NeoForge tag 等价物。
     * 事件在 {@code serverAboutToStart}（SERVER 脚本加载后）触发一次。
     */
    DispatchKey<TagEventJS, String> TAG_REGISTRY_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(TagEventJS event) {
            return event.getRegistry();
        }
    };
    EventBusJS<TagEventJS, String> TAGS =
            GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY);

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

    /**
     * 数据生成事件：脚本把 datapack JSON 写入 {@code <worldDir>/data}（loot tables /
     * advancements / functions），随后由命令处理器调用 {@code server.reload()} 使内容生效。
     */
    EventBusJS<DataGeneratorJS, String> GENERATE_DATA =
            GROUP.server("generateData", DataGeneratorJS.class, STAGE_KEY);

    // 服务器生命周期事件：FMLServer*Event 继承 FMLEvent（而非 eventhandler.Event），
    // 不走 MinecraftForge.EVENT_BUS，无法用 EventBusForgeBridge 订阅。
    // 这里仅声明 bus（不加入 FORGE_BRIDGE），由 NekoJSMod 的 @Mod.EventHandler 转发 post。
    // SERVER 脚本在 FMLServerAboutToStartEvent 时加载（见 NekoJSMod.serverAboutToStart），
    // 因此脚本能监听 starting/started/stopping/stopped；aboutToStart 在脚本加载后立即 post。
    EventBusJS<FMLServerAboutToStartEvent, Void> ABOUT_TO_START =
            GROUP.server("aboutToStart", FMLServerAboutToStartEvent.class);
    EventBusJS<FMLServerStartingEvent, Void> STARTING =
            GROUP.server("starting", FMLServerStartingEvent.class);
    EventBusJS<FMLServerStartedEvent, Void> STARTED =
            GROUP.server("started", FMLServerStartedEvent.class);
    EventBusJS<FMLServerStoppingEvent, Void> STOPPING =
            GROUP.server("stopping", FMLServerStoppingEvent.class);
    EventBusJS<FMLServerStoppedEvent, Void> STOPPED =
            GROUP.server("stopped", FMLServerStoppedEvent.class);

    EventBusJS<LootTableLoadEvent, Void> LOOT_TABLE_LOAD =
            GROUP.server("lootTableLoad", LootTableLoadEvent.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bind(WORLD_LOAD)
            .bind(WORLD_UNLOAD)
            .bind(LOOT_TABLE_LOAD);
}
