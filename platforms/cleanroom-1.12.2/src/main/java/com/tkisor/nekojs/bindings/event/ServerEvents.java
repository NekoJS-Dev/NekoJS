package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableLoadEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;
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

public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    // tick 事件：1.12.2 单一类带 phase，cleanroom bridge 的 bind(bus, filter) 不支持 transformed，
    // 因此 tickPre/tickPost 保持原生 TickEvent.ServerTickEvent
    EventBusJS<TickEvent.ServerTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.ServerTickEvent.class);
    EventBusJS<TickEvent.ServerTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.ServerTickEvent.class);

    EventBusJS<WorldEvent.Load, Void> WORLD_LOAD =
            GROUP.server("worldLoad", WorldEvent.Load.class);
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

    // 统一 wrapper：服务器生命周期 → ServerLifecycleEventJS（非分发）。
    // 1.12.2 的 FMLServer*Event 继承 FMLEvent（而非 eventhandler.Event），
    // 不走 MinecraftForge.EVENT_BUS，无法用 FORGE_BRIDGE 订阅。
    // 由 NekoJSMod 的 @Mod.EventHandler 手动构造 wrapper 并 post（见 NekoJSMod）。
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

    // 统一 wrapper：lootTableLoad → LootTableLoadEventJS（非分发）
    EventBusJS<LootTableLoadEventJS, Void> LOOT_TABLE_LOAD =
        GROUP.server("lootTableLoad", LootTableLoadEventJS.class);

    // —— 1.12.2 LootTableLoadEvent → LootTableLoadEventJS transformer ——
    private static LootTableLoadEventJS fromLootTableLoad(LootTableLoadEvent event) {
        return new LootTableLoadEventJS(event.getName().toString(), event.getTable());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            // tick 保持原生 + filter：transformed-bind-with-filter 不存在于 cleanroom bridge
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bind(WORLD_LOAD)
            .bind(WORLD_UNLOAD)
            .bindTransformed(LOOT_TABLE_LOAD, ServerEvents::fromLootTableLoad, LootTableLoadEvent.class);

    // —— 服务器生命周期手动 post helper ——
    // 由 NekoJSMod 在各 @Mod.EventHandler 中调用：把 FMLServer*Event 转成 wrapper 再投递。
    // SERVER 脚本在 FMLServerAboutToStartEvent 时加载（见 NekoJSMod.serverAboutToStart）。
    // 注意：仅 FMLServerAboutToStartEvent/FMLServerStartingEvent 提供 getServer()，
    // Started/Stopping/Stopped 通过 FMLCommonHandler 取当前服务器实例。
    static void postAboutToStart(FMLServerAboutToStartEvent event) {
        ABOUT_TO_START.post(new ServerLifecycleEventJS(event.getServer()));
    }

    static void postStarting(FMLServerStartingEvent event) {
        STARTING.post(new ServerLifecycleEventJS(event.getServer()));
    }

    static void postStarted(FMLServerStartedEvent event) {
        STARTED.post(new ServerLifecycleEventJS(currentServer()));
    }

    static void postStopping(FMLServerStoppingEvent event) {
        STOPPING.post(new ServerLifecycleEventJS(currentServer()));
    }

    static void postStopped(FMLServerStoppedEvent event) {
        STOPPED.post(new ServerLifecycleEventJS(currentServer()));
    }

    private static net.minecraft.server.MinecraftServer currentServer() {
        return net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
    }
}
