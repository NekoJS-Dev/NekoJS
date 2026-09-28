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
 * {@code BlockBehaviour.BlockStateBase#randomTick} HEAD——与 NeoForge 侧
 * {@code BlockBehaviourMixin} 同点孪生（javap 实证 minecraft-merged-deobf-26.1.2：
 * public 方法，描述符一致）。
 *
 * <p>注入点必须在 BlockStateBase 漏斗而非 {@code BlockBehaviour} 接口 default
 * randomTick：ServerLevel 逐位置虚分派到 BlockStateBase.randomTick 再进具体方块覆写，
 * 原版随机 tick 方块全部覆写 4 参方法，接口 default 对它们永不执行（ticket 24 D5，
 * 两 loader 同病）。ServerLevel 不逐方块检查 {@code isRandomlyTicking()}，因此这里
 * 守卫该标志以维持「仅自然随机 tick 方块触发」的文档语义。
 *
 * <p>高频守卫：无监听器时零事件对象（post 入口 hasListeners 短路）。不可取消
 * （NF 侧同）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockBehaviourRandomTick {

    @Inject(method = "randomTick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"))
    private void nekojs$onRandomTick(ServerLevel level, BlockPos pos, RandomSource random,
                                     CallbackInfo ci) {
        if (!FabricBlockEventBindingsV2.RANDOM_TICK.hasListeners()) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (!state.isRandomlyTicking()) {
            return;
        }
        FabricBlockEventBindingsV2.postRandomTick(level, pos, state, random);
    }
}
