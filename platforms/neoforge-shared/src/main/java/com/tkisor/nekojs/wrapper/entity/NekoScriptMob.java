package com.tkisor.nekojs.wrapper.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class NekoScriptMob extends PathfinderMob {
    public NekoScriptMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        GoalRegistry.applyBuiltInGoals(this);
    }
}
