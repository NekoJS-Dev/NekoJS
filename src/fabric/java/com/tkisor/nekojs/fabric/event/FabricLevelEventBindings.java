package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.LevelEvents;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Fabric bridge for level events: loaded, unloaded, tickPre and tickPost. The payloads are
 * neutral and their member names match the getters of the NeoForge level event. The explosion
 * events (explosionStart/explosionDetonate) and saved need mixins on Level#explode and
 * ServerLevel#save and come in a later batch.
 */
public final class FabricLevelEventBindings {

    private FabricLevelEventBindings() {}

    public static void register() {
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        bridge.bind(LevelEvents.LOADED,
                listener -> ServerLevelEvents.LOAD.register((server, level) ->
                        listener.accept(new LevelEventJS(level))));
        bridge.bind(LevelEvents.UNLOADED,
                listener -> ServerLevelEvents.UNLOAD.register((server, level) ->
                        listener.accept(new LevelEventJS(level))));
        bridge.bind(LevelEvents.TICK_PRE,
                listener -> ServerTickEvents.START_LEVEL_TICK.register(level ->
                        listener.accept(new LevelEventJS(level))));
        bridge.bind(LevelEvents.TICK_POST,
                listener -> ServerTickEvents.END_LEVEL_TICK.register(level ->
                        listener.accept(new LevelEventJS(level))));
    }
}
