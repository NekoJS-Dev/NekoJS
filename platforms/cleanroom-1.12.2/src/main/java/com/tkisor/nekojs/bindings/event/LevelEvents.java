package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 世界（维度）相关事件总线声明：加载/卸载/保存、tick 与爆炸事件。
 */
public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    EventBusJS<WorldEvent.Load, Void> LOADED =
            GROUP.server("loaded", WorldEvent.Load.class);
    EventBusJS<WorldEvent.Unload, Void> UNLOADED =
            GROUP.server("unloaded", WorldEvent.Unload.class);
    EventBusJS<WorldEvent.Save, Void> SAVED =
            GROUP.server("saved", WorldEvent.Save.class);

    // 1.12.2 单一类带 phase：tickPre/tickPost 用 filter 拆分
    EventBusJS<TickEvent.WorldTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.WorldTickEvent.class);
    EventBusJS<TickEvent.WorldTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.WorldTickEvent.class);

    EventBusJS<ExplosionEvent.Start, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionEvent.Start.class);
    EventBusJS<ExplosionEvent.Detonate, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", ExplosionEvent.Detonate.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(LOADED)
            .bind(UNLOADED)
            .bind(SAVED)
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bind(EXPLOSION_START)
            .bind(EXPLOSION_DETONATE);
}
