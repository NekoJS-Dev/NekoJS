package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.EntityType;

/**
 * 实体事件面的 fabric 桥 v1：joinLevel（按实体类型 dispatch）+ death（同）。
 *
 * <p>总线名与 dispatch 语义和 NeoForge 侧一致（脚本 {@code EntityEvents.joinLevel('minecraft:zombie', ...)}
 * 跨加载器同写法）；payload 为中立类，成员名对齐原生事件 getter（entity/level/source）。
 * fabric 的 ENTITY_LOAD 只在服务端世界触发，天然满足「SERVER 总线只投递服务端实例」的约定。
 */
public final class FabricEntityEventBindings {

    /** 与 NeoForge 侧 bindings/event/EntityEvents 同名（fabric 子集）。 */
    public static final EventGroup ENTITY_EVENTS = EventGroup.of("EntityEvents");

    public static final EventBusJS<LivingDeathEventJS, EntityType<?>> DEATH =
            ENTITY_EVENTS.server("death", LivingDeathEventJS.class,
                    EventBusFactory.createDispatchKey(EntityType.class, event -> event.getEntity().getType()));

    private FabricEntityEventBindings() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NekoJS-Fabric");

    public static void register() {
        LOGGER.info("FabricEntityEventBindings registering");
        // joinLevel 暂缺：fabric-api 无「实体加入世界（含 summon/生成）」等价事件
        //（ENTITY_LOAD 仅覆盖从存储装载），待 fabric mixin 通道落地再接——见 MIGRATION-ROADMAP P4。
        // death 用 ALLOW_DEATH（死亡判定前、可取消，语义对齐 NeoForge LivingDeathEvent；
        // death 用 ALLOW_DEATH：死亡判定前触发，语义对齐 NeoForge LivingDeathEvent（同为死亡判定点）
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            DEATH.post(new LivingDeathEventJS(entity, source), entity.getType());
            return true;
        });
    }
}
