package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code BlockEvents.farmlandTrample} 的 fabric 挂点：
 * {@code FarmlandBlock#fallOn} 内 {@code turnToDirt} 调用点（INVOKE 注入）。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>26.x 类名为 {@code FarmlandBlock}（不是 1.21.x 的 {@code FarmBlock}）：
 *       {@code public void fallOn(Level, BlockState, BlockPos, Entity, double)}，
 *       方法体判定链：{@code level instanceof ServerLevel} → 随机数
 *       {@code < 0.5 + fallDistance} → {@code LivingEntity} → 玩家或
 *       {@code MOB_GRIEFING=true} → {@code bbWidth^2 * bbHeight > 0.512F} →
 *       {@code turnToDirt(entity, state, level, pos)}。</li>
 *   <li><b>注入点说明</b>：原设计想在 {@code turnToDirt} 调用点（偏移 97）注入，
 *       但 INVOKE 扫 0（static 调用点 + 本 fork 的选择器组合的已知怪癖），
 *       改为 {@code Entity.getBbWidth()} 调用点（偏移 71，invokevirtual、
 *       CP owner == 声明类，与本文档同批次其他已验证挂点同型）——此点在体型
 *       检查之前，即 ServerLevel/随机/LivingEntity/玩家或 MOB_GRIEFING 判定
 *       已全部通过。偏差：体型过小（{@code bbWidth^2*bbHeight <= 0.512}）时
 *       本事件多触发（该情况下原版也不会塌地）；取消语义不变——{@code ci.cancel()}
 *       后会跳过体型检查与 {@code turnToDirt}（以及 {@code super.fallOn}），
 *       实体不再把耕地踩塌成土路。</li>
 * </ul>
 *
 * <p>客户端说明：{@code turnToDirt} 调用点位于 {@code instanceof ServerLevel}
 * 守卫之后，客户端路径不会到达；仍保留一层 {@code isClientSide()} 短路以抗未来
 * 原版改动（SERVER 总线只收服务端实例）。
 */
@Mixin(FarmlandBlock.class)
public abstract class MixinFarmlandBlock {

    @Inject(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBbWidth()F"),
            cancellable = true)
    private void nekojs$onTrample(Level level, BlockState state, BlockPos pos, Entity entity,
                                  double fallDistance, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postFarmlandTrample(
                level, pos, state, (float) fallDistance, entity)) {
            ci.cancel();
        }
    }
}
