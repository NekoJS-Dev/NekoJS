package com.tkisor.nekojs.bindings.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

public final class ServerLevelEventThread {
    private ServerLevelEventThread() {}

    public static boolean isCurrent(LevelAccessor level) {
        return level instanceof ServerLevel serverLevel && serverLevel.getServer().isSameThread();
    }
}
