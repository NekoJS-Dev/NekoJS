package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code EntityEvents.tickPre / tickPost} 的 fabric 挂点：{@code Entity#tick} 的
 * HEAD/TAIL。另经 {@code FabricEntityEventBindingsV2.ensureRegistered()} 惰性注册
 * leaveLevel 的 fabric-api {@code ServerEntityEvents.ENTITY_UNLOAD} 回调。
 *
 * <p>为何不用 fabric-api：entity-events-v1 5.0.2 javap 实证无 per-entity tick 回调
 * （仅 Elytra/Sleep/Combat/Living 伤害死亡转换）。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code public void tick()}——{@code Entity} 抽象类上的<b>具体</b>方法
 *       （子类可覆写；覆写且不调 {@code super.tick()} 的实体不会触达本挂点）。</li>
 *   <li>{@code level()} public 具体方法（javap 实证），客户端也参与 tick——两个注入点
 *       都以 {@code level().isClientSide()} 过滤，SERVER 总线只收服务端实例。</li>
 * </ul>
 *
 * <p><b>开销说明</b>：每 tick 每实体都会走到注入点（含无监听器时），注入实现只做
 * {@code isClientSide + hasListeners} 两次短路检查（{@code hasListeners()} 在
 * {@code FabricEntityEventBindingsV2} 内部先于 payload 构建），无监听器时零事件对象。
 * 仍属高频通道：脚本监听器请保持轻量、建议只在需要时注册。
 */
@Mixin(Entity.class)
public abstract class MixinEntityTick {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nekojs$onTickPre(CallbackInfo ci) {
        if (((Entity) (Object) this).level().isClientSide()) {
            return;
        }
        // 惰性注册（幂等）：第一个服务端实体 tick 必然早于任何 leaveLevel 场景
        FabricEntityEventBindingsV2.ensureRegistered();
        FabricEntityEventBindingsV2.postTickPre((Entity) (Object) this);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void nekojs$onTickPost(CallbackInfo ci) {
        if (((Entity) (Object) this).level().isClientSide()) {
            return;
        }
        FabricEntityEventBindingsV2.postTickPost((Entity) (Object) this);
    }
}
