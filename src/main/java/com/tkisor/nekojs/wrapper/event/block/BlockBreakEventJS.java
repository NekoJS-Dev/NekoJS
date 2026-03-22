package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.wrapper.block.BlockWrapper;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import lombok.Getter;
import net.neoforged.neoforge.event.level.BlockEvent;

public class BlockBreakEventJS implements NekoCancellableEvent {
    private final BlockEvent.BreakEvent rawEvent;

    @Getter
    private final PlayerWrapper player;

    @Getter
    private final BlockWrapper block;

    public BlockBreakEventJS(BlockEvent.BreakEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.player = new PlayerWrapper(rawEvent.getPlayer());

        this.block = new BlockWrapper(
                rawEvent.getLevel(),
                rawEvent.getPos(),
                rawEvent.getState()
        );
    }

    public String getBlockId() {
        return this.block.getId();
    }

}