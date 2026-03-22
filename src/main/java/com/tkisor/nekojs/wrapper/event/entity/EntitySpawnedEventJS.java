package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import com.tkisor.nekojs.wrapper.level.LevelWrapper;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class EntitySpawnedEventJS implements NekoCancellableEvent {

    private final EntityJoinLevelEvent rawEvent;

    public EntitySpawnedEventJS(EntityJoinLevelEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    /**
     * 获取生成的实体
     * JS 侧: event.entity
     */
    public EntityWrapper getEntity() {
        return new EntityWrapper(rawEvent.getEntity());
    }

    /**
     * 获取实体所在的世界
     * JS 侧: event.level
     */
    public LevelWrapper getLevel() {
        return new LevelWrapper(rawEvent.getLevel());
    }

}