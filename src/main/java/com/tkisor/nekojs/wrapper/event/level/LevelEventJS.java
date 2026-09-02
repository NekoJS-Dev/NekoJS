package com.tkisor.nekojs.wrapper.event.level;

import lombok.Getter;
import net.minecraft.server.level.ServerLevel;

/**
 * 维度（Level）事件的中立 payload（loaded / unloaded / tickPre / tickPost 共用；
 * 成员 {@code event.level}，与 NeoForge 原生 LevelEvent 的 getLevel() 同形）。
 */
public class LevelEventJS {

    @Getter
    private final ServerLevel level;

    public LevelEventJS(ServerLevel level) {
        this.level = level;
    }
}
