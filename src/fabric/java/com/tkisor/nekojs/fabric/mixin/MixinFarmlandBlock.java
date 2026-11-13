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
 * Fabric hook for {@code BlockEvents.farmlandTrample}: the {@code turnToDirt} call site inside
 * {@code FarmlandBlock#fallOn} (INVOKE injection).
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>In 26.x the class is {@code FarmlandBlock}, not the 1.21.x {@code FarmBlock}. It has
 *       {@code public void fallOn(Level, BlockState, BlockPos, Entity, double)}, whose body tests
 *       in order: {@code level instanceof ServerLevel}, a random number
 *       {@code < 0.5 + fallDistance}, {@code LivingEntity}, a player or
 *       {@code MOB_GRIEFING=true}, {@code bbWidth^2 * bbHeight > 0.512F}, then
 *       {@code turnToDirt(entity, state, level, pos)}.</li>
 *   <li><b>Injection point</b>: the original plan was the {@code turnToDirt} call site
 *       (offset 97), but the INVOKE scan found nothing (a known quirk with static call sites and
 *       this fork's selector combination). The mixin uses the {@code Entity.getBbWidth()} call
 *       site instead (offset 71, invokevirtual with the CP owner equal to the declaring class,
 *       the same shape as other verified hooks in this batch). That point comes after the
 *       ServerLevel, random, LivingEntity and player-or-MOB_GRIEFING tests have all passed.
 *       The difference: an entity that is too small ({@code bbWidth^2*bbHeight <= 0.512}) still
 *       fires the event, though vanilla would not trample in that case either. Cancelling behaves
 *       the same: {@code ci.cancel()} skips the size check, {@code turnToDirt} and
 *       {@code super.fallOn}, so the entity no longer turns farmland into dirt.</li>
 * </ul>
 *
 * <p>Client side: the {@code turnToDirt} call site sits after the {@code instanceof ServerLevel}
 * guard, so the client path never reaches it. The mixin still keeps an {@code isClientSide()}
 * short-circuit in case vanilla changes, because the SERVER bus only receives server-side
 * instances.
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
