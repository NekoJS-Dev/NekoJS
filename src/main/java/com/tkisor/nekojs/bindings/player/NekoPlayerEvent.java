package com.tkisor.nekojs.bindings.player;

import com.tkisor.nekojs.bindings.entity.NekoLivingEntityEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface NekoPlayerEvent extends NekoLivingEntityEvent {
    @Override
    Player getEntity();

    @Override
    @Nullable
    default Player getPlayer() {
        return getEntity();
    }
}
