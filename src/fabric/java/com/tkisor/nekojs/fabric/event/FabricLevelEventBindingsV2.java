package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.LevelEvents;
import com.tkisor.nekojs.wrapper.event.level.LevelExplosionEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelSavedEventJS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;

/**
 * Fabric bridge v2 for level event surface: saved / explosionStart / explosionDetonate
 * (+ {@code beforeExplosion} / {@code afterExplosion} historical aliases).
 *
 * <p>The bus is declared in <b>this class</b> and registered into the node-twin
 * {@link LevelEvents}' {@code GROUP} (same-name group, merged at load time)—it shares a
 * namespace with v1's loaded/unloaded/tickPre/tickPost; scripts see a single complete
 * {@code LevelEvents}. Names and aliases match the NeoForge LevelEvents implementation:
 * <ul>
 *   <li>{@code saved} — NeoForge {@code LevelEvent.Save} (uncancellable).</li>
 *   <li>{@code explosionStart} / {@code beforeExplosion} (Deprecated) —
 *       NeoForge {@code ExplosionEvent.Start} (cancellable).</li>
 *   <li>{@code explosionDetonate} / {@code afterExplosion} (Deprecated) —
 *       NeoForge {@code ExplosionEvent.Detonate} (informational).</li>
 * </ul>
 *
 * <p><b>Critical initialization discipline:</b> {@code EventGroup} freezes after plugin
 * bootstrapping (see {@code EventsPoint#freezeEventGroups})—{@code GROUP.add} for bus fields in
 * this class must occur before freezing. Sequencing: {@code NekoJSFabricMod.onInitialize} first
 * runs each {@code register()} (before plugin boot/freezing in {@code initializeScripts()}),
 * so calling {@link #bootstrap()} in {@code FabricLevelEventBindings.register()} is sufficient.
 * Do not delay until the first mixin post (by then, it is frozen and {@code GROUP.add} throws
 * an IllegalStateException).
 */
public final class FabricLevelEventBindingsV2 {

    private FabricLevelEventBindingsV2() {}

    /** Level save complete (server script, uncancellable). Payload {@link LevelSavedEventJS}. */
    public static final EventBusJS<LevelSavedEventJS, Void> SAVED =
            LevelEvents.GROUP.server("saved", LevelSavedEventJS.class);

    /** Before explosion (server script; cancellable—if cancelled, the entire explosion is not processed). */
    public static final EventBusJS<LevelExplosionEventJS, Void> EXPLOSION_START =
            LevelEvents.GROUP.add("explosionStart", ScriptType.SERVER,
                    EventBusJS.of(LevelExplosionEventJS.class, true));

    /** beforeExplosion: historical alias for explosionStart (same deprecated status as NeoForge side). */
    @Deprecated
    public static final EventBusJS<LevelExplosionEventJS, Void> BEFORE_EXPLOSION =
            LevelEvents.GROUP.add("beforeExplosion", ScriptType.SERVER,
                    EventBusJS.of(LevelExplosionEventJS.class, true));

    /** Explosion detonation complete (server script, informational). */
    public static final EventBusJS<LevelExplosionEventJS, Void> EXPLOSION_DETONATE =
            LevelEvents.GROUP.server("explosionDetonate", LevelExplosionEventJS.class);

    /** afterExplosion: historical alias for explosionDetonate (same deprecated status as NeoForge side). */
    @Deprecated
    public static final EventBusJS<LevelExplosionEventJS, Void> AFTER_EXPLOSION =
            LevelEvents.GROUP.server("afterExplosion", LevelExplosionEventJS.class);

    /**
     * Triggers initialization of this class, registering the buses above into {@code LevelEvents.GROUP}.
     * Must be called before the event group freezes (plugin bootstrap): the only safe timing is early
     * in {@code NekoJSFabricMod.onInitialize} (add a line to {@code FabricLevelEventBindings.register()}).
     */
    public static void bootstrap() {
        // Empty body: touching any bus constant is enough to finish the class initialisation
    }

    /**
     * Level save complete: {@code MixinServerLevelSave} called at the TAIL of
     * {@code ServerLevel#save(ProgressListener, boolean, boolean)}.
     */
    public static void postSaved(ServerLevel level) {
        if (!SAVED.hasListeners()) {
            return;
        }
        SAVED.post(new LevelSavedEventJS(level));
    }

    /**
     * Before explosion: {@code MixinServerExplosion} called at the HEAD of
     * {@code ServerExplosion#explode()}.
     *
     * @return {@code true} if a listener cancelled the explosion (mixin should skip {@code explode()} execution)
     */
    public static boolean postExplosionStart(Explosion explosion) {
        if (!EXPLOSION_START.hasListeners() && !BEFORE_EXPLOSION.hasListeners()) {
            return false;
        }
        LevelExplosionEventJS payload = new LevelExplosionEventJS(explosion.level(), explosion);
        boolean cancelled = EXPLOSION_START.post(payload);
        cancelled |= BEFORE_EXPLOSION.post(payload);
        return cancelled;
    }

    /**
     * Explosion resolved: {@code MixinServerExplosion} calls this at the TAIL of
     * {@code ServerExplosion#explode()}, after explosion damage, block breaking and fire are done;
     * the client explosion packet is sent afterwards by {@code ServerLevel.explode}.
     */
    public static void postExplosionDetonate(Explosion explosion) {
        if (!EXPLOSION_DETONATE.hasListeners() && !AFTER_EXPLOSION.hasListeners()) {
            return;
        }
        LevelExplosionEventJS payload = new LevelExplosionEventJS(explosion.level(), explosion);
        EXPLOSION_DETONATE.post(payload);
        AFTER_EXPLOSION.post(payload);
    }
}
