package com.tkisor.nekojs.bindings.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StableBlockPlacement {
    private StableBlockPlacement() {}

    public static boolean shouldCancel(BlockItem item, BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel();
        if (level.isClientSide() || level.isDebug() || !BlockEvents.hasStablePlacementListeners()) return false;

        Class<?> itemType = item.getClass();
        Class<?> blockType = state.getBlock().getClass();
        BlockPos pos = context.getClickedPos();
        Player actor = context.getPlayer();
        if (!level.isInWorldBounds(pos)) return false;

        BlockPos secondary = null;
        if (itemType == BedItem.class && blockType == BedBlock.class) {
            secondary = pos.relative(state.getValue(BedBlock.FACING));
        } else if (itemType == DoubleHighBlockItem.class && (blockType == DoorBlock.class
                || blockType == DoublePlantBlock.class || blockType == SmallDripleafBlock.class
                || blockType == TallFlowerBlock.class)) {
            secondary = pos.above();
        } else if (actor == null || !isSingleBlockItem(itemType)) {
            return false;
        }
        if (secondary != null && !level.isInWorldBounds(secondary)) return false;

        var itemId = BuiltInRegistries.ITEM.getKey(item);
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (itemId == null || !itemId.getNamespace().equals("minecraft")
                || blockId == null || !blockId.getNamespace().equals("minecraft")) return false;

        return BlockEvents.postStablePlacement(new Attempt(level, pos.immutable(), secondary, state,
                context.getClickedFace(), actor));
    }

    private static boolean isSingleBlockItem(Class<?> type) {
        return type == BlockItem.class || type == StandingAndWallBlockItem.class || type == BannerItem.class
                || type == GameMasterBlockItem.class || type == HangingSignItem.class || type == PlaceOnWaterBlockItem.class
                || type == PlayerHeadItem.class || type == ScaffoldingBlockItem.class || type == SignItem.class;
    }

    public record Attempt(Level level, BlockPos pos, BlockPos secondary, BlockState state, Direction face, Entity actor) {}
}
