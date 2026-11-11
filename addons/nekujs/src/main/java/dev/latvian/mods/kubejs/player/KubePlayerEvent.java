package dev.latvian.mods.kubejs.player;

import dev.latvian.mods.kubejs.entity.KubeLivingEntityEvent;
import net.minecraft.world.entity.player.Player;

public interface KubePlayerEvent extends KubeLivingEntityEvent {
    @Override
    Player getEntity();

    @Override
    default Player getPlayer() {
        return getEntity();
    }
}
