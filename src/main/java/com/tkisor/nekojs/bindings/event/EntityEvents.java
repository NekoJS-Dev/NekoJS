package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.function.Function;

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    EventBusJS<LivingDamageEvent.Pre, EntityType<?>> DAMAGE_PRE =
            GROUP.server("damagePre", LivingDamageEvent.Pre.class, dispatchByEntityType());
    EventBusJS<LivingDamageEvent.Post, EntityType<?>> DAMAGE_POST =
            GROUP.server("damagePost", LivingDamageEvent.Post.class, dispatchByEntityType());

    EventBusJS<LivingDeathEvent, EntityType<?>> DEATH =
            GROUP.server("death", LivingDeathEvent.class, dispatchByEntityType());
    EventBusJS<EntityTickEvent.Pre, EntityType<?>> TICK_Pre =
            GROUP.server("tickPre", EntityTickEvent.Pre.class, dispatchByEntityType());
    EventBusJS<EntityTickEvent.Post, EntityType<?>> TICK_Post =
            GROUP.server("tickPost", EntityTickEvent.Post.class, dispatchByEntityType());
    EventBusJS<EntityJoinLevelEvent, EntityType<?>> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityJoinLevelEvent.class, dispatchByEntityType());
    EventBusJS<EntityLeaveLevelEvent, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEvent.class, dispatchByEntityType());

    private static  <T extends EntityEvent> DispatchKey<T, EntityType<?>> dispatchByEntityType() {
        return DispatchKey.of(EntityType.class, e -> e.getEntity().getType());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bind(DAMAGE_PRE)
        .bind(DAMAGE_POST)
        .bind(DEATH)
        .bind(TICK_Pre)
        .bind(TICK_Post)
        .bind(JOIN_LEVEL)
        .bind(LEAVE_LEVEL);
}