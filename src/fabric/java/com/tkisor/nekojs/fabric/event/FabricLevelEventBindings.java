package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.LevelEvents;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Fabric bridge for level events: loaded, unloaded, tickPre and tickPost. The payloads are
 * neutral and their member names match the getters of the NeoForge level event. The explosion
 * events (explosionStart/explosionDetonate) and saved need mixins on Level#explode and
 * ServerLevel#save and come in a later batch.
 */
public final class FabricLevelEventBindings {

    private FabricLevelEventBindings() {}

    public static boolean isReadyOverworld(LevelEventJS event) {
        return event instanceof ReadyOverworldEvent;
    }

    public static boolean isStableLoad(LevelEventJS event) {
        return isReadyOverworld(event) || !Level.OVERWORLD.equals(event.getLevel().dimension());
    }

    public static void postReadyOverworld(ServerLevel level) {
        if (LevelEvents.LOADED.hasStableModuleListeners(ScriptType.SERVER)) {
            LevelEvents.LOADED.postStableModuleNotification(new ReadyOverworldEvent(level), ScriptType.SERVER);
        }
    }

    private static final class ReadyOverworldEvent extends LevelEventJS {
        private ReadyOverworldEvent(ServerLevel level) {
            super(level);
        }
    }

    public static boolean isStableTick(LevelEventJS event) {
        return event instanceof StableTickEvent;
    }

    public static void postStableTick(ServerLevel level, boolean pre) {
        EventBusJS<LevelEventJS, Void> bus = pre ? LevelEvents.TICK_PRE : LevelEvents.TICK_POST;
        if (bus.hasStableModuleListeners(ScriptType.SERVER)) {
            bus.postStableModuleNotification(new StableTickEvent(level), ScriptType.SERVER);
        }
    }

    private static final class StableTickEvent extends LevelEventJS {
        private StableTickEvent(ServerLevel level) {
            super(level);
        }
    }

    /** Registers Fabric event callbacks onto the corresponding NekoJS script event buses. */
    public static void register() {
        FabricLevelEventBindingsV2.bootstrap();
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
