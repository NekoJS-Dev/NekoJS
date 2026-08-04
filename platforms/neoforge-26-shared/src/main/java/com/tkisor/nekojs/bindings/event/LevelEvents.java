package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    // 跨平台统一 wrapper：所有 LevelEvents 均为非分发（Void key）
    EventBusJS<LevelEventJS, Void> LOADED =
            GROUP.server("loaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> UNLOADED =
            GROUP.server("unloaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> SAVED =
            GROUP.server("saved", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_POST =
            GROUP.server("tickPost", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK =
            GROUP.server("tick", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> EXPLOSION_START =
            GROUP.server("explosionStart", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> BEFORE_EXPLOSION =
            GROUP.server("beforeExplosion", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> AFTER_EXPLOSION =
            GROUP.server("afterExplosion", LevelEventJS.class);

    // —— NeoForge LevelEvent/ExplosionEvent/LevelTickEvent → LevelEventJS transformer ——
    // 统一字段名：level（从 getLevel()）。LevelTickEvent 不继承 LevelEvent，单独处理。
    private static LevelEventJS fromLevelEvent(LevelEvent event) {
        return new LevelEventJS(event.getLevel());
    }

    private static LevelEventJS fromTick(LevelTickEvent event) {
        return new LevelEventJS(event.getLevel());
    }

    private static LevelEventJS fromExplosion(ExplosionEvent event) {
        return new LevelEventJS(event.getLevel()).withExplosion(event.getExplosion());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(LOADED, LevelEvents::fromLevelEvent, LevelEvent.Load.class)
            .bindTransformed(UNLOADED, LevelEvents::fromLevelEvent, LevelEvent.Unload.class)
            .bindTransformed(SAVED, LevelEvents::fromLevelEvent, LevelEvent.Save.class)
            .bindTransformed(TICK_PRE, LevelEvents::fromTick, LevelTickEvent.Pre.class)
            .bindTransformed(TICK_POST, LevelEvents::fromTick, LevelTickEvent.Post.class)
            .bindTransformed(TICK, LevelEvents::fromTick, LevelTickEvent.Post.class)
            .bindTransformed(EXPLOSION_START, LevelEvents::fromExplosion, ExplosionEvent.Start.class)
            .bindTransformed(BEFORE_EXPLOSION, LevelEvents::fromExplosion, ExplosionEvent.Start.class)
            .bindTransformed(EXPLOSION_DETONATE, e ->
                    fromExplosion(e).withDetonate(e.getAffectedBlocks(), e.getAffectedEntities()),
                    ExplosionEvent.Detonate.class)
            .bindTransformed(AFTER_EXPLOSION, e ->
                    fromExplosion(e).withDetonate(e.getAffectedBlocks(), e.getAffectedEntities()),
                    ExplosionEvent.Detonate.class);
}
