package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.api.event.TargetedEventHandler;
import com.tkisor.nekojs.wrapper.event.entity.EntityDeathEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityHurtPostEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityHurtPreEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntitySpawnedEventJS;

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    TargetedEventHandler<EntityHurtPreEventJS> HURT_PRE =
            GROUP.targetedServer("hurtPre", () -> EntityHurtPreEventJS.class);

    TargetedEventHandler<EntityHurtPostEventJS> HURT_POST =
            GROUP.targetedServer("hurtPost", () -> EntityHurtPostEventJS.class);

    TargetedEventHandler<EntityDeathEventJS> DEATH =
            GROUP.targetedServer("death", () -> EntityDeathEventJS.class);
    EventHandler<EntitySpawnedEventJS> SPAWNED =
            GROUP.server("spawned", () -> EntitySpawnedEventJS.class);
}