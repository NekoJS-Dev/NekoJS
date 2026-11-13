package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral payload for the player changed dimension event
 * ({@code PlayerEvents.changedDimension}).
 *
 * <p>The members follow NeoForge {@code PlayerEvent.PlayerChangedDimensionEvent}:
 * {@code event.player} / {@code event.from} (getFrom, the old dimension) / {@code event.to}
 * (getTo, the new dimension), both typed {@code ResourceKey<Level>} (the level registration
 * key).
 *
 * <p>26.1.2 platform fact: vanilla {@code ServerPlayer#changeDimension(...)} no longer exists,
 * and dimension changes now happen inside {@code ServerPlayer#teleport(TeleportTransition)}.
 * NeoForge 26.1.2 also fires PlayerChangedDimensionEvent at the end of that method, and the
 * fabric hook matches it.
 */
@Doc("Fired when a player changes dimension (PlayerEvents.changedDimension).")
@Doc("event.from / event.to are ResourceKey<Level> of the old and new dimensions.")
@Getter
public class PlayerChangedDimensionEventJS {

    @Doc("The player who changed dimension.")
    private final ServerPlayer player;

    @Doc("The dimension the player left (ResourceKey<Level>).")
    private final ResourceKey<Level> from;

    @Doc("The dimension the player entered (ResourceKey<Level>).")
    private final ResourceKey<Level> to;

    public PlayerChangedDimensionEventJS(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }
}
