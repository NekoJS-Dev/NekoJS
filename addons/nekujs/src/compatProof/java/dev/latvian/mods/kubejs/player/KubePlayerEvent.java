package dev.latvian.mods.kubejs.player;

import dev.latvian.mods.kubejs.entity.KubeLivingEntityEvent;
import net.minecraft.world.entity.player.Player;

/** Preserves the covariant player contract and its generated interface bridges. */
public interface KubePlayerEvent extends KubeLivingEntityEvent {
    @Override
    Player getEntity();

    @Override
    default Player getPlayer() {
        return getEntity();
    }
}
