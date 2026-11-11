package dev.latvian.mods.kubejs.level;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Compatibility view over an explicitly supplied native level and immutable position. */
public class BlockContainerJS implements LevelBlock {
    private final Level level;
    private final BlockPos position;
    public BlockContainerJS(Level level, BlockPos position) {
        this.level = Objects.requireNonNull(level);
        this.position = Objects.requireNonNull(position).immutable();
    }
    @Override public Level getLevel() { return level; }
    @Override public BlockPos getPos() { return position; }
}
