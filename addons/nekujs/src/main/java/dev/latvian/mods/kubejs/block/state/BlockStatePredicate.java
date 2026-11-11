package dev.latvian.mods.kubejs.block.state;

import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface BlockStatePredicate extends Predicate<BlockState> {
    @Override boolean test(BlockState state);
    default boolean testBlock(Block block) { return test(block.defaultBlockState()); }
}
