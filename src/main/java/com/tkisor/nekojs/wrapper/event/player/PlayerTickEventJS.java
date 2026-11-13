package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-neutral payload for the player tick event (shared by tickPre / tickPost; the member
 * {@code event.player} has the same shape as NeoForge native PlayerTickEvent's getPlayer()).
 */
public class PlayerTickEventJS {

    @Getter
    private final Player player;

    public PlayerTickEventJS(Player player) {
        this.player = player;
    }
}
