package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** 维度（Level）事件组（server 脚本）：加载/卸载/保存、tick 与爆炸各阶段。 */
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
    // tick：tickPost 的裸名别名。脚本侧建议迁移到 tickPre/tickPost（H-5 别名裁决，2026-08-15）。
    @Deprecated
    EventBusJS<LevelTickEvent.Post, Void> TICK =
            GROUP.server("tick", LevelTickEvent.Post.class);
    EventBusJS<ExplosionEvent.Start, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionEvent.Start.class);
    // beforeExplosion：explosionStart 的历史别名。脚本侧建议迁移到 explosionStart。
    @Deprecated
    EventBusJS<ExplosionEvent.Start, Void> BEFORE_EXPLOSION =
            GROUP.server("beforeExplosion", ExplosionEvent.Start.class);
    EventBusJS<ExplosionEvent.Detonate, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", ExplosionEvent.Detonate.class);
    // afterExplosion：explosionDetonate 的历史别名。脚本侧建议迁移到 explosionDetonate。
    @Deprecated
    EventBusJS<ExplosionEvent.Detonate, Void> AFTER_EXPLOSION =
            GROUP.server("afterExplosion", ExplosionEvent.Detonate.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOADED)
            .bind(UNLOADED)
            .bind(SAVED)
            // LevelTickEvent 双逻辑侧触发：SERVER 总线只投递服务端实例（客户端 level tick 在 Render 线程）
            .bind(TICK_PRE, e -> !e.getLevel().isClientSide())
            .bind(TICK_POST, e -> !e.getLevel().isClientSide())
            .bind(TICK, e -> !e.getLevel().isClientSide())
            .bind(EXPLOSION_START)
            .bind(BEFORE_EXPLOSION)
            .bind(EXPLOSION_DETONATE)
            .bind(AFTER_EXPLOSION);
}
