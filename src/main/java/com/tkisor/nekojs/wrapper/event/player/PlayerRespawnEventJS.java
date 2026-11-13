package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral payload for the player respawn event. The member {@code event.player} is the
 * entity after respawning, {@code oldPlayer} is the entity before it, and {@code alive} tells
 * whether the old entity is still alive: true for a respawn that returns from the End, false
 * for a death respawn. fabric's AFTER_RESPAWN passes all three.
 */
public class PlayerRespawnEventJS {

    @Getter
    private final ServerPlayer player;

    @Getter
    private final ServerPlayer oldPlayer;

    @Getter
    private final boolean alive;

    public PlayerRespawnEventJS(ServerPlayer player, ServerPlayer oldPlayer, boolean alive) {
        this.player = player;
        this.oldPlayer = oldPlayer;
        this.alive = alive;
    }
}
