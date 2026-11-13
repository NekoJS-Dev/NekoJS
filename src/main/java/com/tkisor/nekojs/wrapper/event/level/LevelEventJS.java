package com.tkisor.nekojs.wrapper.event.level;

import lombok.Getter;
import net.minecraft.server.level.ServerLevel;

/**
 * Loader-neutral payload for level events (shared by loaded / unloaded / tickPre / tickPost;
 * the member {@code event.level} has the same shape as NeoForge native LevelEvent's getLevel()).
 */
public class LevelEventJS {

    @Getter
    private final ServerLevel level;

    public LevelEventJS(ServerLevel level) {
        this.level = level;
    }
}
