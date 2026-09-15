package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code EntityEvents.drops} 的 fabric 挂点：{@code LivingEntity#dropAllDeathLoot}。
 *
 * <p>为何不用 fabric-api：{@code ServerLivingEntityEvents} 5.0.2 javap 实证只有
 * ALLOW_DAMAGE / AFTER_DAMAGE / ALLOW_DEATH / AFTER_DEATH / MOB_CONVERSION——
 * <b>没有掉落回调</b>。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel,
 *       net.minecraft.world.damagesource.DamageSource)}——26.x 签名（1.21.x 无 ServerLevel 参数），
 *       protected 具体方法，HEAD 可注入。</li>
 *   <li>{@code LivingEntity#die(DamageSource)} 字节码 91-93 先 {@code dead=true}，
 *       109-150 服务端检查后、{@code createWitherRose} 前调用 {@code dropAllDeathLoot}——
 *       本挂点是所有生物（含玩家）死亡掉落的单一入口。</li>
 * </ul>
 *
 * <p>取消语义：{@code EntityEvents.drops} 为可取消总线，监听器 {@code return true} →
 * {@code ci.cancel()} 跳过整段原版掉落（equipment + 战利品表 + 经验球），等价 NeoForge
 * {@code LivingDropsEvent} 的 setCanceled；脚本 push 进 {@code event.drops} 的物品由
 * {@code FabricEntityEventBindingsV2.postDrops} 经 {@code Entity#spawnAtLocation}
 * （public，字节码实证最终 {@code ServerLevel.addFreshEntity}）放出。
 * 限制：原版生成的掉落集合在 vanilla 里不存在（逐件生成），payload 的 {@code drops}
 * 触发时为空（详见 {@code LivingDropsEventJS} javadoc）。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDeathDrops {

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onDropAllDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (FabricEntityEventBindingsV2.postDrops((LivingEntity) (Object) this, level, source)) {
            ci.cancel();
        }
    }
}
