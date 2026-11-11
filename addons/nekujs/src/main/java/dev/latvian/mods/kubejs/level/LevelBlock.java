package dev.latvian.mods.kubejs.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Borrowed level and position; no global server lookup or retained world cache. */
public interface LevelBlock {
    Level getLevel();
    BlockPos getPos();
    default BlockState getBlockState() { return getLevel().getBlockState(getPos()); }
    default int getX() { return getPos().getX(); }
    default int getY() { return getPos().getY(); }
    default int getZ() { return getPos().getZ(); }
}
