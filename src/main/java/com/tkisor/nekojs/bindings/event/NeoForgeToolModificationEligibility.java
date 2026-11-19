package com.tkisor.nekojs.bindings.event;

import net.minecraft.core.Direction;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.level.BlockEvent;

final class NeoForgeToolModificationEligibility {
    private NeoForgeToolModificationEligibility() {}

    static boolean isEligible(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.getFinalState() == null
                || !ServerLevelEventThread.isCurrent(event.getLevel())) {
            return false;
        }

        ItemAbility ability = event.getItemAbility();
        if (!event.getHeldItemStack().canPerformAction(ability)) {
            return false;
        }

        BlockState state = event.getState();
        if (ability == ItemAbilities.AXE_STRIP) {
            return AxeItem.getAxeStrippingState(state) != null;
        }
        if (ability == ItemAbilities.AXE_SCRAPE) {
            return WeatheringCopper.getPrevious(state).isPresent();
        }
        if (ability == ItemAbilities.AXE_WAX_OFF) {
            return DataMapHooks.getBlockUnwaxed(state.getBlock()) != null;
        }
        if (ability == ItemAbilities.SHOVEL_FLATTEN) {
            var context = event.getContext();
            return context.getClickedFace() != Direction.DOWN
                    && ShovelItem.getShovelPathingState(state) != null
                    && context.getLevel().getBlockState(event.getPos().above()).isAir();
        }
        if (ability == ItemAbilities.SHOVEL_DOUSE) {
            return event.getContext().getClickedFace() != Direction.DOWN
                    && state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
        }
        if (ability == ItemAbilities.HOE_TILL) {
            Block block = state.getBlock();
            return block == Blocks.ROOTED_DIRT
                    || ((block == Blocks.GRASS_BLOCK || block == Blocks.DIRT_PATH || block == Blocks.DIRT
                            || block == Blocks.COARSE_DIRT) && HoeItem.onlyIfAirAbove(event.getContext()));
        }
        if (ability == ItemAbilities.SHEARS_TRIM) {
            return state.getBlock() instanceof GrowingPlantHeadBlock plant && !plant.isMaxAge(state);
        }
        if (ability == ItemAbilities.FIRESTARTER_LIGHT) {
            return CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state);
        }
        return false;
    }
}
