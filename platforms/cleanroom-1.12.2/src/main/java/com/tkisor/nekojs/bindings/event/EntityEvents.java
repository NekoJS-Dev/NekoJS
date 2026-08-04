package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.entity.EntityEventJS;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    // 跨平台统一 wrapper：dispatch key 从 wrapper.entity 提取（1.12.2 按 Entity 分发）
    EventBusJS<EntityEventJS, Entity> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));
    EventBusJS<EntityEventJS, Entity> DEATH =
            GROUP.server("death", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));
    EventBusJS<EntityEventJS, Entity> DAMAGE_PRE =
            GROUP.server("damagePre", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));
    EventBusJS<EntityEventJS, Entity> DAMAGE_POST =
            GROUP.server("damagePost", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));
    EventBusJS<EntityEventJS, Entity> DROPS =
            GROUP.server("drops", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));
    EventBusJS<EntityEventJS, Entity> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Entity.class, EntityEventJS::getEntity));

    // useItem 事件按 Item 分发
    EventBusJS<EntityEventJS, Item> USE_ITEM_STARTED =
            GROUP.server("useItemStarted", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem()));
    EventBusJS<EntityEventJS, Item> USE_ITEM_STOPPED =
            GROUP.server("useItemStopped", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem()));
    EventBusJS<EntityEventJS, Item> USE_ITEM_FINISHED =
            GROUP.server("useItemFinished", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem()));
    EventBusJS<EntityEventJS, Item> USE_ITEM_TICK =
            GROUP.server("useItemTick", EntityEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem()));

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bindTransformed(JOIN_LEVEL, e ->
                    new EntityEventJS(e.getEntity()).withLevel(e.getWorld()), EntityJoinWorldEvent.class)
            .bindTransformed(DEATH, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()), LivingDeathEvent.class)
            .bindTransformed(DAMAGE_PRE, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()).withAmount(e.getAmount()),
                    LivingHurtEvent.class)
            .bindTransformed(DAMAGE_POST, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()).withAmount(e.getAmount()),
                    LivingDamageEvent.class)
            .bindTransformed(DROPS, e ->
                    new EntityEventJS(e.getEntity()).withSource(e.getSource()).withDrops(e.getDrops()),
                    LivingDropsEvent.class)
            .bindTransformed(FINALIZE_SPAWN, e ->
                    new EntityEventJS(e.getEntity()), LivingSpawnEvent.CheckSpawn.class)
            .bindTransformed(USE_ITEM_STARTED, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Start.class)
            .bindTransformed(USE_ITEM_STOPPED, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Stop.class)
            .bindTransformed(USE_ITEM_FINISHED, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration())
                            .withResult(e.getResultStack()),
                    LivingEntityUseItemEvent.Finish.class)
            .bindTransformed(USE_ITEM_TICK, e ->
                    new EntityEventJS(e.getEntity()).withItem(e.getItem()).withDuration(e.getDuration()),
                    LivingEntityUseItemEvent.Tick.class);
}
