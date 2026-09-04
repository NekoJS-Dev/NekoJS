package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 实体 tick 事件（{@code EntityEvents.tickPre / tickPost}）的**加载器中立**载荷。
 *
 * <p>成员对齐 NeoForge 原生 {@code EntityTickEvent}（getEntity / getLevel）。fabric 侧
 * 没有 per-entity tick 现成回调（fabric-api 5.0.2 的 entity-events-v1
 * 仅 Elytra/Sleep/Combat/Living 伤害死亡转换，javap 实证），由
 * {@code FabricEntityEventBindingsV2} + {@code MixinEntityTick} 从
 * {@code Entity#tick} 的 HEAD/TAIL 转换（HEAD 传调用前、TAIL 传调用后）。
 *
 * <p><b>开销与覆盖说明</b>：{@code Entity#tick} 每 tick 每实体都会执行，总线
 * 内部以 {@code hasListeners()} 短路（无监听器时零 payload 构建）；覆写了
 * {@code tick()} 且未调 {@code super.tick()} 的实体不会触达（个别
 * 实体类型，见 {@code MixinEntityTick} javadoc）。
 *
 * <p>不可取消；SERVER 总线只投递服务端实例（mixin 侧过滤客户端）。
 */
@Doc("Fired before / after each entity's tick (EntityEvents.tickPre / tickPost). Dispatched by entity type.")
@Doc("Not cancellable. Server-side instances only. High-frequency: use carefully.")
@Getter
public class EntityTickEventJS {

    @Doc("The ticking entity.")
    private final Entity entity;

    @Doc("The level the entity is in.")
    private final Level level;

    public EntityTickEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
