//? if neoforge {
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.NeoForgeBlockEvents;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块随机 tick 注入：在 {@code BlockBehaviour.BlockStateBase.randomTick} HEAD 触发
 * {@link RandomTickEvent}（脚本侧 {@code BlockEvents.randomTick}）。
 *
 * <p>注入点必须在 BlockStateBase 漏斗，不能在 {@code BlockBehaviour} 接口 default
 * randomTick：ServerLevel 对随机 tick 区段内每个位置虚分派
 * {@code BlockState.randomTick(level, pos, random)}（即 BlockStateBase.randomTick），
 * 由它再分派到具体方块覆写；原版所有随机 tick 方块都覆写了 4 参 randomTick（如草方块
 * SpreadingSnowyDirtBlock），接口 default（空体）对它们永不执行——旧注入点对原版随机
 * tick 方块不可达（ticket 24 D5）。
 *
 * <p>ServerLevel 调用本漏斗时不逐方块检查 {@code isRandomlyTicking()}（区段内任意位置
 * 都会进来，非 tick 方块靠空方法体兜底），因此这里守卫 {@code isRandomlyTicking()} 以
 * 维持文档语义：仅自然随机 tick 的方块触发（对标原版 / KubeJS 语义）。无监听器时零
 * 事件对象（randomTick 高频，性能守卫）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockBehaviourMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void nekojs$fireRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!NeoForgeBlockEvents.RANDOM_TICK.hasListeners()) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (!state.isRandomlyTicking()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new RandomTickEvent(level, pos, state, random));
    }
}
//?}
