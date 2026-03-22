package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.wrapper.event.entity.EntityDeathEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityHurtPostEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityHurtPreEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntitySpawnedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChatEventJS;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    EventBusJS<EntityHurtPreEventJS, String> HURT_PRE =
            GROUP.server("hurtPre", EntityHurtPreEventJS.class, DispatchKey.string());

    EventBusJS<EntityHurtPostEventJS, String> HURT_POST =
            GROUP.server("hurtPost", EntityHurtPostEventJS.class, DispatchKey.string());

    EventBusJS<EntityDeathEventJS, String> DEATH =
            GROUP.server("death", EntityDeathEventJS.class, DispatchKey.string());
    EventBusJS<EntitySpawnedEventJS, Void> SPAWNED =
            GROUP.server("spawned", EntitySpawnedEventJS.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(DEATH, EntityDeathEventJS::new, LivingDeathEvent.class)
            .bindTransformed(SPAWNED, EntitySpawnedEventJS::new, EntityJoinLevelEvent.class);
}