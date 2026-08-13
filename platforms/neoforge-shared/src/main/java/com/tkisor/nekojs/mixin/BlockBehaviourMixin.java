package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块随机 tick 注入：在 {@code BlockBehaviour.randomTick} HEAD 触发
 * {@link RandomTickEvent}（脚本侧 {@code BlockEvents.randomTick}）。
 *
 * <p>原版只在 {@code isRandomlyTicking()} 为 true 的方块上调用 randomTick，
 * 因此本事件仅对自然随机 tick 的方块触发（对标原版 / KubeJS 语义）。
 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.class)
public abstract class BlockBehaviourMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void nekojs$fireRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // 无监听器时不构造事件（randomTick 高频，性能守卫）
        if (!BlockEvents.RANDOM_TICK.canDispatch()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new RandomTickEvent(level, pos, state, random));
    }
}
