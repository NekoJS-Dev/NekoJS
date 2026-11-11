package dev.latvian.mods.kubejs.entity;

import dev.latvian.mods.kubejs.level.KubeLevelEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Entity defaults are real accesses, never successful dummy world values. */
public interface KubeEntityEvent extends KubeLevelEvent {
    Entity getEntity();

    default Player getPlayer() {
        return getEntity() instanceof Player player ? player : null;
    }

    @Override
    default Level getLevel() {
        return getEntity().level();
    }
}
