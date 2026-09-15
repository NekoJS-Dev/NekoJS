// fabric 孪生：NeoForge 侧同位次实现见 src/main/java/com/tkisor/nekojs/mixin/BlockBehaviourMixin.java
// （经 NF 总线投递原生 RandomTickEvent；fabric 侧 mixin 直投中立总线，孪生决策见移植台账第 14 批）。
package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块随机 tick 事件（{@code BlockEvents.randomTick}）的 fabric 挂点：
 * {@code BlockBehaviour#randomTick} HEAD——与 NeoForge 侧 {@code BlockBehaviourMixin}
 * 同点孪生（javap 实证 minecraft-merged-deobf-26.1.2：protected 方法，描述符一致）。
 *
 * <p>高频守卫：无监听器时零事件对象（post 入口 hasListeners 短路）。原版只对
 * {@code isRandomlyTicking()} 方块调用且子类覆写不经过基类——语义与 NF 侧一致。
 * 不可取消（NF 侧同）。
 */
@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviourRandomTick {

    @Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"))
    private void nekojs$onRandomTick(BlockState state, ServerLevel level, BlockPos pos,
                                     RandomSource random, CallbackInfo ci) {
        FabricBlockEventBindingsV2.postRandomTick(level, pos, state, random);
    }
}
