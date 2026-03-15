package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.block.BlockWrapper;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import lombok.Getter;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BlockRightClickEventJS implements NekoEvent {
    private final PlayerInteractEvent.RightClickBlock rawEvent;

    @Getter
    private final PlayerWrapper player;

    @Getter
    private final BlockWrapper block;

    @Getter
    private final ItemStackWrapper item;

    @Getter
    private boolean cancelled;

    public BlockRightClickEventJS(PlayerInteractEvent.RightClickBlock rawEvent) {
        this.rawEvent = rawEvent;

        this.player = new PlayerWrapper(rawEvent.getEntity());

        this.item = new ItemStackWrapper(rawEvent.getItemStack());

        Level level = rawEvent.getLevel();
        BlockPos pos = rawEvent.getPos();
        BlockState state = level.getBlockState(pos);
        this.block = new BlockWrapper(level, pos, state);
    }

    public String getBlockId() {
        return this.block.getId();
    }

    public String getFacing() {
        return rawEvent.getFace() != null ? rawEvent.getFace().getSerializedName() : "null";
    }

    public String getHand() {
        return rawEvent.getHand().name();
    }

    public void cancel() {
        rawEvent.setCanceled(true);
        this.cancelled = true;

    }
}