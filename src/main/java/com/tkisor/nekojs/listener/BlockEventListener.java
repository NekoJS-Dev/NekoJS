package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class BlockEventListener {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState blockState = event.getState();
        BlockEvents.BROKEN.post(event, blockState.getBlock().neko$getId());
    }

    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        BlockState blockState = event.getLevel().getBlockState(event.getPos());
        BlockEvents.RIGHT_CLICKED.post(event, blockState.getBlock().neko$getId());
    }

    @SubscribeEvent
    public static void onBlockLeftClicked(PlayerInteractEvent.LeftClickBlock event) {
        BlockState blockState = event.getLevel().getBlockState(event.getPos());
        BlockEvents.LEFT_CLICKED.post(event, blockState.getBlock().neko$getId());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        BlockEvents.PLACED.post(event, event.getPlacedBlock().getBlock().neko$getId());
    }
}