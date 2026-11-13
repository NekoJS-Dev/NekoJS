package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for the block placement events ({@code BlockEvents.placed} /
 * {@code BlockEvents.entityPlaced}): the HEAD of
 * {@code EnderMan$EndermanLeaveBlockGoal#canPlaceBlock}.
 *
 * <p>Why this point (checked with javap on minecraft-merged-deobf-26.1.2): vanilla picks a
 * random position in {@code tick()} and then calls
 * {@code canPlaceBlock(level, pos, carried, targetState, belowState, below)}, its only caller,
 * so all the context is in the declared parameters. That is much simpler than hooking the
 * {@code Level.setBlock} INVOKE point in tick and capturing locals. Cancelling means
 * {@code cir.setReturnValue(false)}, so the {@code &&} in {@code tick} short-circuits,
 * {@code setBlock} does not run and the enderman keeps the block in hand. This matches the
 * NeoForge 26.1.2 patch, where {@code EventHooks.onBlockPlace} returning true skips placement.
 *
 * <p>Platform fact for 26.x: the only post point of {@code BlockEvent.EntityPlaceEvent} on
 * NeoForge is this same place (the patch source is {@code EventHooks.onBlockPlace};
 * BlockItem, FallingBlockEntity and WitherBoss have no hook). On both platforms this event
 * fires only when an enderman places a block, and one placement posts on both buses (placed and
 * entityPlaced).
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class MixinEndermanLeaveBlockGoal {

    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "canPlaceBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onEndermanPlace(Level level, BlockPos pos, BlockState carried,
                                        BlockState targetState, BlockState belowState,
                                        BlockPos below, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postPlaced(level, pos, carried, belowState, enderman)) {
            cir.setReturnValue(false);
        }
    }
}
