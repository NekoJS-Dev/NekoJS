package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * Loader-neutral payload for the player entity interact event
 * ({@code PlayerEvents.entityInteract}).
 *
 * <p>The members follow the getters of NeoForge
 * {@code PlayerInteractEvent.EntityInteract}: {@code event.player} / {@code event.target}
 * (getTarget, the entity that was right-clicked) / {@code event.hand} (getHand).
 *
 * <p>Cancellable: when a listener returns {@code true}, fabric cancels the handling of the
 * whole {@code ServerboundInteractPacket} (so {@code ServerPlayer#interactOn} does not run),
 * which matches NeoForge's ICancellableEvent. Fires on the server only and has nothing to do
 * with attacking an entity (handleAttack).
 */
@Doc("Fired when a player interacts (right-clicks) an entity (PlayerEvents.entityInteract).")
@Doc("Return true to cancel the interaction.")
@Getter
public class PlayerEntityInteractEventJS {

    @Doc("The interacting player.")
    private final ServerPlayer player;

    @Doc("The entity being interacted with.")
    private final Entity target;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    public PlayerEntityInteractEventJS(ServerPlayer player, Entity target, InteractionHand hand) {
        this.player = player;
        this.target = target;
        this.hand = hand;
    }
}
