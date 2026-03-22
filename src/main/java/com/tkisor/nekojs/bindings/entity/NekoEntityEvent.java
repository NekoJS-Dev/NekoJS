package com.tkisor.nekojs.bindings.entity;

import com.tkisor.nekojs.bindings.level.NekoLevelEvent;
import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface NekoEntityEvent extends NekoLevelEvent {
    EntityWrapper getEntity();

    @Nullable
    default PlayerWrapper getPlayer() {
        return getEntity() instanceof PlayerWrapper p ? p : null;
    }

    @Override
    default Level getLevel() {
        return getEntity().level();
    }
}
