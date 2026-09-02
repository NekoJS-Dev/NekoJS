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
        ServerLevelEvents.LOAD.register((server, level) ->
                LevelEvents.LOADED.post(new LevelEventJS(level)));
        ServerLevelEvents.UNLOAD.register((server, level) ->
                LevelEvents.UNLOADED.post(new LevelEventJS(level)));
        ServerTickEvents.START_LEVEL_TICK.register(level ->
                LevelEvents.TICK_PRE.post(new LevelEventJS(level)));
        ServerTickEvents.END_LEVEL_TICK.register(level ->
                LevelEvents.TICK_POST.post(new LevelEventJS(level)));
    }
}
