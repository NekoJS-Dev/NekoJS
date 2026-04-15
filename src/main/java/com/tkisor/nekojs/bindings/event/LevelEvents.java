package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    EventBusJS<ExplosionEvent.Start, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionEvent.Start.class);
    EventBusJS<ExplosionEvent.Detonate, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", ExplosionEvent.Detonate.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(EXPLOSION_START)
            .bind(EXPLOSION_DETONATE);
}
