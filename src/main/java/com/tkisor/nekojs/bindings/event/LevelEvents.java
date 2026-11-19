//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableExplosionEvent;
import com.tkisor.nekojs.api.event.StableLevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Level (dimension) event group for server scripts: load, unload, save, tick, and explosion stages.
 */
@Doc("Level (dimension) event group for server scripts: load, unload, save, tick, and explosion stages.")
public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    /** Fired when a server level (dimension) finishes loading. */
    @Doc("Fired when a server level (dimension) finishes loading.")
    EventBusJS<LevelEvent.Load, Void> LOADED =
            GROUP.server("loaded", LevelEvent.Load.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableLevelEvent.class, NeoForgeStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a server level (dimension) is unloaded. */
    @Doc("Fired when a server level (dimension) is unloaded.")
    EventBusJS<LevelEvent.Unload, Void> UNLOADED =
            GROUP.server("unloaded", LevelEvent.Unload.class, api -> {
                api.versioned().when(NeoForgeLevelUnloadRouting::isVersionedUnload);
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && NeoForgeLevelUnloadRouting.isStableUnload(event))
                        .view(StableLevelEvent.class, NeoForgeStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a server level (dimension) is saved to disk. */
    @Doc("Fired when a server level (dimension) is saved to disk.")
    EventBusJS<LevelEvent.Save, Void> SAVED =
            GROUP.server("saved", LevelEvent.Save.class, api -> {
                api.versioned().when(NeoForgeLevelSaveRouting::isVersioned);
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && NeoForgeLevelSaveRouting.isStable(event))
                        .view(StableLevelEvent.class, NeoForgeStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired at the start of each server level tick. */
    @Doc("Fired at the start of each server level tick.")
    EventBusJS<LevelTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", LevelTickEvent.Pre.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableLevelEvent.class, NeoForgeStableEventProjections::levelTick)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired at the end of each server level tick. */
    @Doc("Fired at the end of each server level tick.")
    EventBusJS<LevelTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", LevelTickEvent.Post.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableLevelEvent.class, NeoForgeStableEventProjections::levelTick)
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * Legacy alias for {@link #TICK_POST}. Scripts should migrate to {@code tickPre} or {@code tickPost}.
     */
    @Doc("Deprecated alias for tickPost. Prefer tickPre or tickPost.")
    @Deprecated
    EventBusJS<LevelTickEvent.Post, Void> TICK =
            GROUP.server("tick", LevelTickEvent.Post.class, api -> api.versioned());

    /** Fired before an explosion begins computing affected blocks and entities. */
    @Doc("Fired before an explosion begins computing affected blocks and entities.")
    EventBusJS<ExplosionEvent.Start, Void> EXPLOSION_START =
            GROUP.server("explosionStart", ExplosionEvent.Start.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableExplosionEvent.class, NeoForgeStableEventProjections::explosionStart)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * Legacy alias for {@link #EXPLOSION_START}. Scripts should migrate to {@code explosionStart}.
     */
    @Doc("Deprecated alias for explosionStart.")
    @Deprecated
    EventBusJS<ExplosionEvent.Start, Void> BEFORE_EXPLOSION =
            GROUP.server("beforeExplosion", ExplosionEvent.Start.class, api -> api.versioned());

    /** Fired when an explosion detonates and applies damage and block destruction. */
    @Doc("Fired when an explosion detonates and applies damage and block destruction.")
    EventBusJS<ExplosionEvent.Detonate, Void> EXPLOSION_DETONATE =
            GROUP.server("explosionDetonate", ExplosionEvent.Detonate.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && event.getExplosion().radius() >= 1.0e-5f)
                        .view(StableExplosionEvent.class, NeoForgeStableEventProjections::explosionDetonate)
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * Legacy alias for {@link #EXPLOSION_DETONATE}. Scripts should migrate to {@code explosionDetonate}.
     */
    @Doc("Deprecated alias for explosionDetonate.")
    @Deprecated
    EventBusJS<ExplosionEvent.Detonate, Void> AFTER_EXPLOSION =
            GROUP.server("afterExplosion", ExplosionEvent.Detonate.class, api -> api.versioned());

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOADED, e -> !e.getLevel().isClientSide())
            .bind(UNLOADED, e -> !e.getLevel().isClientSide())
            .bind(SAVED)
            // LevelTickEvent fires on both logical sides: the SERVER bus delivers only server instances
            // (client level tick runs on the Render thread).
            .bind(TICK_PRE, e -> !e.getLevel().isClientSide())
            .bind(TICK_POST, e -> !e.getLevel().isClientSide())
            .bind(TICK, e -> !e.getLevel().isClientSide())
            .bind(EXPLOSION_START, e -> !e.getLevel().isClientSide())
            .bind(BEFORE_EXPLOSION, e -> !e.getLevel().isClientSide())
            .bind(EXPLOSION_DETONATE, e -> !e.getLevel().isClientSide())
            .bind(AFTER_EXPLOSION, e -> !e.getLevel().isClientSide());
}
//?}
