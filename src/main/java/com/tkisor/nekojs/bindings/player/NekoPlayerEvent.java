package com.tkisor.nekojs.bindings.player;

import com.tkisor.nekojs.bindings.entity.NekoLivingEntityEvent;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface NekoPlayerEvent extends NekoLivingEntityEvent {
    @Override
    PlayerWrapper getEntity();

    @Override
    @Nullable
    default PlayerWrapper getPlayer() {
        return getEntity();
    }
}
