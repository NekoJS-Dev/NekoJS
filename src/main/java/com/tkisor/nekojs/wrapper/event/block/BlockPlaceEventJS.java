package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.block.BlockWrapper;
import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.level.BlockEvent;

public class BlockPlaceEventJS implements NekoCancellableEvent {
    private final BlockEvent.EntityPlaceEvent rawEvent;

    @Getter
    private final EntityWrapper entity;

    @Getter
    private final BlockWrapper block;

    public BlockPlaceEventJS(BlockEvent.EntityPlaceEvent rawEvent) {
        this.rawEvent = rawEvent;

        this.entity = EntityWrapper.of(rawEvent.getEntity());

        this.block = new BlockWrapper(
                rawEvent.getLevel(),
                rawEvent.getPos(),
                rawEvent.getPlacedBlock()
        );
    }

    public String getBlockId() {
        return this.block.getId();
    }

    public boolean isPlayer() {
        return rawEvent.getEntity() instanceof Player;
    }

    public PlayerWrapper getPlayer() {
        return entity instanceof PlayerWrapper pw ? pw : null;
    }

}