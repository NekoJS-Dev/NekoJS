//? if neoforge {
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.BlockEvents;
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
 * Injects the block random tick: fires {@link RandomTickEvent} (script side
 * {@code BlockEvents.randomTick}) at the HEAD of {@code BlockBehaviour.BlockStateBase.randomTick}.
 *
 * <p>The injection point must be BlockStateBase, not the default randomTick of the
 * {@code BlockBehaviour} interface. ServerLevel virtually dispatches
 * {@code BlockState.randomTick(level, pos, random)}, which is BlockStateBase.randomTick, for every
 * position in a random tick section, and that method dispatches on to the concrete block override.
 * Every vanilla random tick block overrides the four-argument randomTick (grass, for example, is
 * SpreadingSnowyDirtBlock), so the empty interface default never runs for them and an injection
 * there is unreachable for vanilla blocks.
 *
 * <p>When ServerLevel calls this entry point it does not check {@code isRandomlyTicking()} per block:
 * any position in the section gets here and non-ticking blocks fall through to an empty body. The
 * guard on {@code isRandomlyTicking()} here keeps the documented behavior, so only blocks that
 * naturally random tick fire, matching vanilla and KubeJS. With no listeners no event object is
 * created, because randomTick runs very often.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockBehaviourMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void nekojs$fireRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!BlockEvents.RANDOM_TICK.hasListeners()) {
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
