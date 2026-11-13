package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral payload for the player entity clone event (the data copy point on death
 * respawn and on returning from the End). The members follow NeoForge
 * {@code PlayerEvent.Clone}: {@code event.player} is the new entity, {@code event.original}
 * the old one, and {@code alive} tells whether the old entity is still alive. fabric's
 * COPY_FROM passes all three.
 */
public class PlayerCloneEventJS {

    @Getter
    private final ServerPlayer player;

    @Getter
    private final ServerPlayer original;

    @Getter
    private final boolean alive;

    public PlayerCloneEventJS(ServerPlayer player, ServerPlayer original, boolean alive) {
        this.player = player;
        this.original = original;
        this.alive = alive;
    }
}
