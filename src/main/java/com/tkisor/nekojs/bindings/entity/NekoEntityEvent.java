package com.tkisor.nekojs.bindings.entity;

import com.tkisor.nekojs.bindings.level.NekoLevelEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface NekoEntityEvent extends NekoLevelEvent {
    Entity getEntity();

    @Nullable
    default Player getPlayer() {
        return getEntity() instanceof Player p ? p : null;
    }

    @Override
    default Level getLevel() {
        return getEntity().level();
    }
}
