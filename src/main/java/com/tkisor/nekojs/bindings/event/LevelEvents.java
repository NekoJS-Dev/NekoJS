package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.level.ExplosionStartEventJS;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");
    EventBusJS<ExplosionStartEventJS, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionStartEventJS.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(EXPLOSION_START, ExplosionStartEventJS::new, ExplosionEvent.Start.class);
}
