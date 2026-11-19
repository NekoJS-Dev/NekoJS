package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for {@code BlockEvents.entityMultiPlaced} on multi-block placements
 * ({@link BedBlock}, {@link DoorBlock}, {@link DoublePlantBlock}).
 *
 * <p>Injects at the {@code RETURN} of {@code getStateForPlacement(BlockPlaceContext)}: when a
 * valid placement state has been resolved on the server and a script cancels the event, setting
 * the return value to {@code null} aborts {@code BlockItem#place} cleanly before either half of
 * the multi-block is placed.
 */
@Mixin({BedBlock.class, DoorBlock.class, DoublePlantBlock.class})
public abstract class MixinMultiBlockPlace {

    @Inject(method = "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"),
            cancellable = true)
    private void nekojs$onMultiBlockPlace(BlockPlaceContext context,
                                          CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null && !context.getLevel().isClientSide()) {
            if (FabricBlockEventBindingsV2.postEntityMultiPlaced(
                    context.getLevel(), context.getClickedPos(), state, context.getPlayer())) {
                cir.setReturnValue(null);
            }
        }
    }
}
