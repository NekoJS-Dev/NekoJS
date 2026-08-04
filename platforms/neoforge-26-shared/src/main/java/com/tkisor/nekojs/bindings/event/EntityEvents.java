package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.entity.EntityEventJS;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    // 跨平台统一 wrapper：dispatch key 从 wrapper 提取（entity.getType()）
    // cancellable 事件显式传 true（wrapper POJO 不携带 cancellability 信息）
    EventBusJS<EntityEventJS, EntityType<?>> DAMAGE_PRE =
            GROUP.server("damagePre", EntityEventJS.class, true, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> DAMAGE_POST =
            GROUP.server("damagePost", EntityEventJS.class, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> DEATH =
            GROUP.server("death", EntityEventJS.class, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> DROPS =
            GROUP.server("drops", EntityEventJS.class, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", EntityEventJS.class, true, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> TICK_Pre =
            GROUP.server("tickPre", EntityEventJS.class, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> TICK_Post =
            GROUP.server("tickPost", EntityEventJS.class, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityEventJS.class, true, dispatchByEntityType());
    EventBusJS<EntityEventJS, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityEventJS.class, true, dispatchByEntityType());

    // useItem 事件按 Item 分发，dispatch key 从 wrapper.item 提取
    EventBusJS<EntityEventJS, Item> USE_START =
            GROUP.server("useItemStarted", EntityEventJS.class, true, dispatchByItem());
    EventBusJS<EntityEventJS, Item> USE_STOP =
            GROUP.server("useItemStopped", EntityEventJS.class, dispatchByItem());
    EventBusJS<EntityEventJS, Item> USE_FINISHED =
            GROUP.server("useItemFinished", EntityEventJS.class, dispatchByItem());
    EventBusJS<EntityEventJS, Item> USE_TICK =
            GROUP.server("useItemTick", EntityEventJS.class, dispatchByItem());

    private static DispatchKey<EntityEventJS, EntityType<?>> dispatchByEntityType() {
        return EventBusFactory.createDispatchKey(EntityType.class, e -> e.getEntity().getType());
    }

    private static DispatchKey<EntityEventJS, Item> dispatchByItem() {
        return EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(DAMAGE_PRE, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()).withAmount(e.getNewDamage()),
                    LivingDamageEvent.Pre.class)
            .bindTransformed(DAMAGE_POST, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()),
                    LivingDamageEvent.Post.class)
            .bindTransformed(DEATH, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()),
                    LivingDeathEvent.class)
            .bindTransformed(DROPS, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource())
                            .withDrops(e.getDrops()),
                    LivingDropsEvent.class)
            .bindTransformed(FINALIZE_SPAWN, e ->
                    new EntityEventJS(e.getEntity()),
                    FinalizeSpawnEvent.class)
            .bindTransformed(TICK_Pre, e ->
                    new EntityEventJS(e.getEntity()), EntityTickEvent.Pre.class)
            .bindTransformed(TICK_Post, e ->
                    new EntityEventJS(e.getEntity()), EntityTickEvent.Post.class)
            .bindTransformed(JOIN_LEVEL, e ->
                    new EntityEventJS(e.getEntity()).withLevel(e.getLevel()),
                    EntityJoinLevelEvent.class)
            .bindTransformed(LEAVE_LEVEL, e ->
                    new EntityEventJS(e.getEntity()).withLevel(e.getLevel()),
                    EntityLeaveLevelEvent.class)
            .bindTransformed(USE_START, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Start.class)
            .bindTransformed(USE_STOP, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Stop.class)
            .bindTransformed(USE_FINISHED, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration())
                            .withResult(e.getResultStack()),
                    LivingEntityUseItemEvent.Finish.class)
            .bindTransformed(USE_TICK, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Tick.class);
}
