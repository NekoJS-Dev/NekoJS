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

public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    EventBusJS<EntityJoinWorldEvent, Entity> JOIN_WORLD =
            GROUP.server("joinWorld", EntityJoinWorldEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            EntityJoinWorldEvent::getEntity));
    EventBusJS<LivingDeathEvent, Entity> DEATH =
            GROUP.server("death", LivingDeathEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDeathEvent::getEntity));
    EventBusJS<LivingHurtEvent, Entity> HURT =
            GROUP.server("hurt", LivingHurtEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingHurtEvent::getEntity));
    EventBusJS<LivingDamageEvent, Entity> DAMAGE =
            GROUP.server("damage", LivingDamageEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDamageEvent::getEntity));
    EventBusJS<LivingDropsEvent, Entity> DROPS =
            GROUP.server("drops", LivingDropsEvent.class,
                    EventBusFactory.createDispatchKey(Entity.class,
                            LivingDropsEvent::getEntity));
    EventBusJS<LivingSpawnEvent.CheckSpawn, Entity> CHECK_SPAWN =
            GROUP.server("checkSpawn", LivingSpawnEvent.CheckSpawn.class,
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
            .bind(JOIN_WORLD)
            .bind(DEATH)
            .bind(HURT)
            .bind(DAMAGE)
            .bind(DROPS)
            .bind(CHECK_SPAWN)
            .bind(USE_ITEM_STARTED)
            .bind(USE_ITEM_STOPPED)
            .bind(USE_ITEM_FINISHED)
            .bind(USE_ITEM_TICK);
}
