package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockBreakEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class BlockEventListener {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockBreakEventJS eventJS = new BlockBreakEventJS(event);
        BlockEvents.BROKEN.post(eventJS.getBlockId(), eventJS);
    }

    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        BlockRightClickEventJS eventJS = new BlockRightClickEventJS(event);
        BlockEvents.RIGHT_CLICKED.post(eventJS.getBlockId(), eventJS);
    }
}