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
