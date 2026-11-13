// Fabric twin of the implementation at the same point on the NeoForge side, in
// src/main/java/com/tkisor/nekojs/mixin/BlockBehaviourMixin.java (that one delivers the vanilla
// RandomTickEvent through the NF bus; the fabric mixin posts to the neutral bus directly).
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
 * Fabric hook for the block random tick event ({@code BlockEvents.randomTick}): the HEAD of
 * {@code BlockBehaviour.BlockStateBase#randomTick}, the same point as {@code BlockBehaviourMixin}
 * on NeoForge (checked with javap on minecraft-merged-deobf-26.1.2: a public method with the
 * same descriptor).
 *
 * <p>The injection has to be on the BlockStateBase funnel rather than the {@code BlockBehaviour}
 * interface default randomTick. ServerLevel dispatches virtually per position to
 * BlockStateBase.randomTick, which then enters the concrete block override, and every vanilla
 * randomly ticking block overrides the four-argument method, so the interface default never runs
 * for them; both loaders have the same problem. ServerLevel does not check
 * {@code isRandomlyTicking()} per block, so this method checks that flag to keep the documented
 * meaning: only blocks that randomly tick naturally fire the event.
 *
 * <p>High-frequency guard: with no listeners no event object is created, because the post entry
 * point short-circuits on hasListeners. Not cancellable, the same as on NeoForge.
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
