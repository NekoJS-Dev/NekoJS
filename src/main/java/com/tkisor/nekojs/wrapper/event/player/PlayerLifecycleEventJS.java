package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * Neutral Fabric login/logout payload exposing the read-only {@code event.player} Bean property.
 * NeoForge uses its native PlayerEvent payload instead, whose getter is getEntity() and property is {@code event.entity}.
 */
public class PlayerLifecycleEventJS {

    @Getter
    private final Player player;

    public PlayerLifecycleEventJS(Player player) {
        this.player = player;
    }
}
