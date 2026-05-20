package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    EventBusJS<LevelEvent.Load, Void> LOADED =
            GROUP.server("loaded", LevelEvent.Load.class);
    EventBusJS<LevelEvent.Unload, Void> UNLOADED =
            GROUP.server("unloaded", LevelEvent.Unload.class);
    EventBusJS<LevelEvent.Save, Void> SAVED =
            GROUP.server("saved", LevelEvent.Save.class);
    EventBusJS<LevelTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", LevelTickEvent.Pre.class);
    EventBusJS<LevelTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", LevelTickEvent.Post.class);
    EventBusJS<LevelTickEvent.Post, Void> TICK =
            GROUP.server("tick", LevelTickEvent.Post.class);
    EventBusJS<ExplosionEvent.Start, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionEvent.Start.class);
    EventBusJS<ExplosionEvent.Start, Void> BEFORE_EXPLOSION =
            GROUP.server("beforeExplosion", ExplosionEvent.Start.class);
    EventBusJS<ExplosionEvent.Detonate, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", ExplosionEvent.Detonate.class);
    EventBusJS<ExplosionEvent.Detonate, Void> AFTER_EXPLOSION =
            GROUP.server("afterExplosion", ExplosionEvent.Detonate.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOADED)
            .bind(UNLOADED)
            .bind(SAVED)
            .bind(TICK_PRE)
            .bind(TICK_POST)
            .bind(TICK)
            .bind(EXPLOSION_START)
            .bind(BEFORE_EXPLOSION)
            .bind(EXPLOSION_DETONATE)
            .bind(AFTER_EXPLOSION);
}
