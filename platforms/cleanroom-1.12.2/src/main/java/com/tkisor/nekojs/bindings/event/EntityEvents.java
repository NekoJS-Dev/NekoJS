package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;

/**
 * 实体相关事件总线声明（含生物事件）。多数 bus 按实体类型 id 分发（如
 * {@code EntityEvents.death('minecraft:creeper', e => ...)}），物品使用类事件按物品 id 分发。
 */
public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    EventBusJS<EntityJoinWorldEvent, Entity> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityJoinWorldEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            EntityJoinWorldEvent::getEntity));
    EventBusJS<LivingDeathEvent, Entity> DEATH =
            GROUP.server("death", LivingDeathEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDeathEvent::getEntity));
    EventBusJS<LivingHurtEvent, Entity> DAMAGE_PRE =
            GROUP.server("damagePre", LivingHurtEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingHurtEvent::getEntity));
    EventBusJS<LivingDamageEvent, Entity> DAMAGE_POST =
            GROUP.server("damagePost", LivingDamageEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDamageEvent::getEntity));
    EventBusJS<LivingDropsEvent, Entity> DROPS =
            GROUP.server("drops", LivingDropsEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDropsEvent::getEntity));
    EventBusJS<LivingSpawnEvent.CheckSpawn, Entity> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", LivingSpawnEvent.CheckSpawn.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingSpawnEvent::getEntity));
    // 使用物品事件：LivingEntityUseItemEvent.getItem() 返回 ItemStack，dispatch key 取 .getItem() → Item
    EventBusJS<LivingEntityUseItemEvent.Start, Item> USE_ITEM_STARTED =
            GROUP.server("useItemStarted", LivingEntityUseItemEvent.Start.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));
    EventBusJS<LivingEntityUseItemEvent.Stop, Item> USE_ITEM_STOPPED =
            GROUP.server("useItemStopped", LivingEntityUseItemEvent.Stop.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> USE_ITEM_FINISHED =
            GROUP.server("useItemFinished", LivingEntityUseItemEvent.Finish.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));
    EventBusJS<LivingEntityUseItemEvent.Tick, Item> USE_ITEM_TICK =
            GROUP.server("useItemTick", LivingEntityUseItemEvent.Tick.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            // EntityJoinWorldEvent 双逻辑侧触发：SERVER 总线只投递服务端实例
            .bind(JOIN_LEVEL, e -> !e.getWorld().isRemote)
            .bind(DEATH)
            .bind(DAMAGE_PRE)
            .bind(DAMAGE_POST)
            .bind(DROPS)
            .bind(FINALIZE_SPAWN)
            .bind(USE_ITEM_STARTED)
            .bind(USE_ITEM_STOPPED)
            .bind(USE_ITEM_FINISHED)
            .bind(USE_ITEM_TICK);
}
