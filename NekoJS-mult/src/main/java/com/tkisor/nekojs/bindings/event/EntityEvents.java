//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
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
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends EntityEvent> DispatchKey<T, EntityType<?>> dispatchByEntityType() {
        return dispatchByEntity(EntityEvent::getEntity);
    }

    private static <T> DispatchKey<T, EntityType<?>> dispatchByEntity(Function<T, ? extends Entity> toEntity) {
        return DispatchKey.of(EntityType.class, event -> toEntity.apply(event).getType());
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
//?} else {
// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// 事件类）。fabric 侧 joinLevel/death/damagePre/damagePost 由 FabricEntityEventBindings 提供
// （组实例各自注册、同名合并）；本孪生提供 drops/finalizeSpawn/tickPre/tickPost/leaveLevel
// （中立 payload，由 FabricEntityEventBindingsV2 + fabric/mixin/* 投递）。
// Note：本组总线只有在 FabricCorePlugin.registerEvents 注册后方对脚本可见——目前
// FabricCorePlugin 只注册了 FabricEntityEventBindings.ENTITY_EVENTS，需补
// registry.register(com.tkisor.nekojs.bindings.event.EntityEvents.GROUP);
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** 实体/生物事件组（server 脚本）：掉落、生成初始化、tick 与离开维度（fabric 子集），按实体类型定向。 */
public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    /** 死亡掉落（server 脚本）：按实体类型 dispatch；return true = 跳过原版掉落（脚本可选 event.drops）。 */
    EventBusJS<LivingDropsEventJS, EntityType<?>> DROPS =
            GROUP.add("drops", ScriptType.SERVER, EventBusJS.of(
                    LivingDropsEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())));

    /** 生成初始化完成（server 脚本）：按实体类型 dispatch；通知型（fabric 不可取消/不可改）。 */
    EventBusJS<MobFinalizeSpawnEventJS, EntityType<?>> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", MobFinalizeSpawnEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getMob().getType()));

    /** 每实体 tick 前（server 脚本）：按实体类型 dispatch；高频，监听器保持轻量。 */
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_PRE =
            GROUP.server("tickPre", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    /** 每实体 tick 后（server 脚本）：按实体类型 dispatch；高频，监听器保持轻量。 */
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_POST =
            GROUP.server("tickPost", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    /** 实体离开维度/世界（server 脚本）：按实体类型 dispatch（fabric-api ENTITY_UNLOAD）。 */
    EventBusJS<EntityLeaveLevelEventJS, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));
}
//?}
