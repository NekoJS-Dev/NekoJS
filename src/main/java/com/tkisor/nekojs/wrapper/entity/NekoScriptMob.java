package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 脚本可注册的通用生物实体：无内置 AI，goal 完全由脚本经
 * {@code GoalEvents.register} / {@link GoalRegistry} 注册。
 */
@Doc("A generic mob entity registrable from scripts: it has no built-in AI, all goals come from GoalEvents.register.")
public class NekoScriptMob extends PathfinderMob {
    public NekoScriptMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** 应用脚本注册的内置 goal（由实体 AI 初始化回调触发）。 */
    @Override
    protected void registerGoals() {
        GoalRegistry.applyBuiltInGoals(this);
    }
}
