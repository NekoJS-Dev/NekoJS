package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 实体离开维度/世界事件（{@code EntityEvents.leaveLevel}）的**加载器中立**载荷。
 *
 * <p>语义近似 NeoForge {@code EntityLeaveLevelEvent}（实体从 level 移除）。fabric 侧
 * via fabric-api {@code ServerEntityEvents.ENTITY_UNLOAD}（fabric-lifecycle-events-v1
 * 4.1.1，FQN 已从 {@code entity.event.v1} 迁到 {@code event.lifecycle.v1}）——
 * 其在 {@code ServerLevel$EntityCallbacks#onTrackingEnd} HEAD 触发
 * （源码实证：实体被移除前，覆盖死亡移除/维度传送/区块卸载）。
 *
 * <p>不可取消。
 */
@Doc("Fired when an entity is unloaded / removed from a ServerLevel (EntityEvents.leaveLevel). Dispatched by entity type.")
@Doc("Not cancellable. Server-side instances only.")
@Getter
public class EntityLeaveLevelEventJS {

    @Doc("The entity leaving the level.")
    private final Entity entity;

    @Doc("The level the entity is leaving.")
    private final Level level;

    public EntityLeaveLevelEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
