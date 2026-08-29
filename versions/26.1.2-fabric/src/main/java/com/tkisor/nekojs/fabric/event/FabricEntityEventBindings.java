package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.entity.EntityJoinLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 实体事件面的 fabric 桥：joinLevel（按实体类型 dispatch，经自建 mixin 钩子——
 * fabric-api 无「实体加入世界（含 summon/生成）」等价事件，ENTITY_LOAD 仅覆盖存储装载）
 * + death（dispatch，经 ServerLivingEntityEvents.ALLOW_DEATH：死亡判定前触发，
 * 语义对齐 NeoForge LivingDeathEvent）。
 *
 * <p>总线名与 dispatch 语义和 NeoForge 侧一致（脚本
 * {@code EntityEvents.joinLevel('minecraft:zombie', ...)} 跨加载器同写法）；
 * payload 为中立类，成员名对齐原生事件 getter（entity/level/source）。
 */
public final class FabricEntityEventBindings {

    /** 与 NeoForge 侧 bindings/event/EntityEvents 同名（fabric 子集）。 */
    public static final EventGroup ENTITY_EVENTS = EventGroup.of("EntityEvents");

    public static final EventBusJS<EntityJoinLevelEventJS, EntityType<?>> JOIN_LEVEL =
            ENTITY_EVENTS.server("joinLevel", EntityJoinLevelEventJS.class,
                    EventBusFactory.createDispatchKey(EntityType.class, event -> event.getEntity().getType()));

    public static final EventBusJS<LivingDeathEventJS, EntityType<?>> DEATH =
            ENTITY_EVENTS.server("death", LivingDeathEventJS.class,
                    EventBusFactory.createDispatchKey(EntityType.class, event -> event.getEntity().getType()));

    private FabricEntityEventBindings() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NekoJS-Fabric");

    public static void register() {
        LOGGER.info("FabricEntityEventBindings registering");
        // joinLevel 由 ServerLevelMixin 在 addEntity 成功后经 postJoinLevel 触发
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            DEATH.post(new LivingDeathEventJS(entity, source), entity.getType());
            return true;
        });
    }

    /** ServerLevelMixin 钩子入口：ServerLevel 接受实体加入时调用（服务端专属，天然满足 SERVER 总线约定）。 */
    public static void postJoinLevel(Entity entity, ServerLevel level) {
        JOIN_LEVEL.post(new EntityJoinLevelEventJS(entity, level), entity.getType());
        // 与 NeoForge 侧 NekoJSMod 的 EntityJoinLevelEvent 监听同职责：应用脚本注册的 goal
        com.tkisor.nekojs.wrapper.entity.GoalRegistry.onEntityJoinLevel(entity, level);
    }
}
