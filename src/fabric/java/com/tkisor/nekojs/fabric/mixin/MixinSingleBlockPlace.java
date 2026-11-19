package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class MixinSingleBlockPlace {
    @WrapOperation(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            order = 900)
    private boolean nekojs$beforeVersionedBlockItemWrite(BlockItem item, BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        var level = context.getLevel();
        if (!level.isClientSide()) {
            var block = state.getBlock();
            var pos = context.getClickedPos();
            var player = context.getPlayer();
            if (block instanceof BedBlock || block instanceof DoorBlock
                    || (block instanceof DoublePlantBlock && !(block instanceof PitcherCropBlock))) {
                if (FabricBlockEventBindingsV2.hasPlacementAliasListeners()
                        && !level.isDebug() && level.isInValidBounds(pos)) {
                    var snapshotPos = nekojs$firstPlacementPos(item, block, level, pos);
                    var snapshotState = player == null ? level.getBlockState(snapshotPos) : state;
                    var against = level.getBlockState(snapshotPos.relative(context.getClickedFace().getOpposite()));
                    if (FabricBlockEventBindingsV2.postPlaced(level, snapshotPos, snapshotState, against, player)) {
                        return false;
                    }
                }
            } else if (player != null) {
                var against = level.getBlockState(pos.relative(context.getClickedFace().getOpposite()));
                if (FabricBlockEventBindingsV2.postPlaced(level, pos, state, against, player)) {
                    return false;
                }
            }
        }
        return original.call(item, context, state);
    }

    @Unique
    private static BlockPos nekojs$firstPlacementPos(BlockItem item, Block block, Level level, BlockPos pos) {
        var blockType = block.getClass();
        if (item.getClass() == DoubleHighBlockItem.class
                && (blockType == DoorBlock.class || blockType == DoublePlantBlock.class || blockType == TallFlowerBlock.class
                        || blockType == SmallDripleafBlock.class)) {
            var upper = pos.above();
            if (level.isInValidBounds(upper)) {
                var clearState = (level.isWaterAt(upper) ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                if (level.getBlockState(upper) != clearState) {
                    return upper;
                }
            }
        }
        return pos;
    }
}
