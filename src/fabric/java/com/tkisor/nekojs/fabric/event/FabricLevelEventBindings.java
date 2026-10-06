package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.LevelEvents;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 维度事件面的 fabric 桥：loaded / unloaded / tickPre / tickPost（中立 payload，
 * 成员名对齐 NeoForge 原生 LevelEvent 的 getter）。爆炸系（explosionStart/Detonate）
 * 与 saved 需 mixin Level#explode / ServerLevel#save，随后续批次。
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
