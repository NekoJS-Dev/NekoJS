package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    // 跨平台统一 wrapper：loaded/unloaded/saved/explosion 用 LevelEventJS
    EventBusJS<LevelEventJS, Void> LOADED =
            GROUP.server("loaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> UNLOADED =
            GROUP.server("unloaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> SAVED =
            GROUP.server("saved", LevelEventJS.class);

    // tick 事件：1.12.2 单一类带 phase，cleanroom bridge 的 bind(bus, filter) 不支持 transformed，
    // 因此 tickPre/tickPost 保持原生 TickEvent.WorldTickEvent
    EventBusJS<TickEvent.WorldTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.WorldTickEvent.class);
    EventBusJS<TickEvent.WorldTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.WorldTickEvent.class);

    EventBusJS<LevelEventJS, Void> EXPLOSION_START =
            GROUP.server("explosionStart", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", LevelEventJS.class);

    // —— 1.12.2 WorldEvent/ExplosionEvent → LevelEventJS transformer ——
    // 统一字段名：level（从 getWorld()）
    private static LevelEventJS fromWorldEvent(WorldEvent event) {
        return new LevelEventJS(event.getWorld());
    }

    private static LevelEventJS fromExplosion(ExplosionEvent event) {
        return new LevelEventJS(event.getWorld()).withExplosion(event.getExplosion());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bindTransformed(LOADED, LevelEvents::fromWorldEvent, WorldEvent.Load.class)
            .bindTransformed(UNLOADED, LevelEvents::fromWorldEvent, WorldEvent.Unload.class)
            .bindTransformed(SAVED, LevelEvents::fromWorldEvent, WorldEvent.Save.class)
            // tick 保持原生 + filter：transformed-bind-with-filter 不存在于 cleanroom bridge
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bindTransformed(EXPLOSION_START, LevelEvents::fromExplosion, ExplosionEvent.Start.class)
            .bindTransformed(EXPLOSION_DETONATE, e ->
                    fromExplosion(e).withDetonate(e.getAffectedBlocks(), e.getAffectedEntities()),
                    ExplosionEvent.Detonate.class);
}
