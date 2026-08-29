package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 实体加入世界事件的中立 payload（fabric 桥首用；成员 {@code event.entity} /
 * {@code event.level}，与 NeoForge 原生 EntityJoinLevelEvent 的 getEntity/getLevel 同形）。
 */
public class EntityJoinLevelEventJS {

    @Getter
    private final Entity entity;

    @Getter
    private final Level level;

    public EntityJoinLevelEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
