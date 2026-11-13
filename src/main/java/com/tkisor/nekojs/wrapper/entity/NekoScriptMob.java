package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * A generic mob entity that scripts can register. It has no built-in AI; all goals are
 * registered from scripts through {@code GoalEvents.register} / {@link GoalRegistry}.
 */
@Doc("A generic mob entity registrable from scripts: it has no built-in AI, all goals come from GoalEvents.register.")
public class NekoScriptMob extends PathfinderMob {
    public NekoScriptMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** Applies the built-in goals registered by scripts; the entity AI init callback triggers it. */
    @Override
    protected void registerGoals() {
        GoalRegistry.applyBuiltInGoals(this);
    }
}
