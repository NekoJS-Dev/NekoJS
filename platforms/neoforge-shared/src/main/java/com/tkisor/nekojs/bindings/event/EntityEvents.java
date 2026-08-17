package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.function.Function;

/** 实体/生物事件组（server 脚本）：伤害、死亡、掉落、tick、进出维度与使用物品等，按实体类型或物品定向。 */
public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    EventBusJS<LivingDamageEvent.Pre, EntityType<?>> DAMAGE_PRE =
            GROUP.server("damagePre", LivingDamageEvent.Pre.class, dispatchByEntity(LivingDamageEvent::getEntity));
    EventBusJS<LivingDamageEvent.Post, EntityType<?>> DAMAGE_POST =
            GROUP.server("damagePost", LivingDamageEvent.Post.class, dispatchByEntity(LivingDamageEvent::getEntity));

    EventBusJS<LivingDeathEvent, EntityType<?>> DEATH =
            GROUP.server("death", LivingDeathEvent.class, dispatchByEntity(LivingDeathEvent::getEntity));
    EventBusJS<LivingDropsEvent, EntityType<?>> DROPS =
            GROUP.server("drops", LivingDropsEvent.class, dispatchByEntity(LivingDropsEvent::getEntity));
    EventBusJS<FinalizeSpawnEvent, EntityType<?>> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", FinalizeSpawnEvent.class, dispatchByEntity(FinalizeSpawnEvent::getEntity));
    EventBusJS<EntityTickEvent.Pre, EntityType<?>> TICK_Pre =
            GROUP.server("tickPre", EntityTickEvent.Pre.class, dispatchByEntityType());
    EventBusJS<EntityTickEvent.Post, EntityType<?>> TICK_Post =
            GROUP.server("tickPost", EntityTickEvent.Post.class, dispatchByEntityType());
    EventBusJS<EntityJoinLevelEvent, EntityType<?>> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityJoinLevelEvent.class, dispatchByEntityType());
    EventBusJS<EntityLeaveLevelEvent, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEvent.class, dispatchByEntityType());
    EventBusJS<LivingEntityUseItemEvent.Start, Item> USE_START =
            GROUP.server("useItemStarted", LivingEntityUseItemEvent.Start.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Stop, Item> USE_STOP =
            GROUP.server("useItemStopped", LivingEntityUseItemEvent.Stop.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> USE_FINISHED =
            GROUP.server("useItemFinished", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Tick, Item> USE_TICK =
            GROUP.server("useItemTick", LivingEntityUseItemEvent.Tick.class, dispatchByItem(LivingEntityUseItemEvent::getItem));

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return EventBusFactory.createDispatchKey(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends EntityEvent> DispatchKey<T, EntityType<?>> dispatchByEntityType() {
        return dispatchByEntity(EntityEvent::getEntity);
    }

    private static <T> DispatchKey<T, EntityType<?>> dispatchByEntity(Function<T, ? extends Entity> toEntity) {
        return EventBusFactory.createDispatchKey(EntityType.class, event -> toEntity.apply(event).getType());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(DAMAGE_PRE)
            .bind(DAMAGE_POST)
            .bind(DEATH)
            .bind(DROPS)
            .bind(FINALIZE_SPAWN)
            // EntityTick/EntityJoinLevel/LeaveLevel 双逻辑侧触发：SERVER 总线只投递服务端实例
            // （客户端实体 tick/join 在 Render 线程，进入 SERVER Context 会被拒绝）
            .bind(TICK_Pre, e -> !e.getEntity().level().isClientSide())
            .bind(TICK_Post, e -> !e.getEntity().level().isClientSide())
            .bind(JOIN_LEVEL, e -> !e.getEntity().level().isClientSide())
            .bind(LEAVE_LEVEL, e -> !e.getEntity().level().isClientSide())
            .bind(USE_START)
            .bind(USE_STOP)
            .bind(USE_FINISHED)
            .bind(USE_TICK);
}
