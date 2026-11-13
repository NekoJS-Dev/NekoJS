package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the player entity interact event
 * ({@code ItemEvents.entityInteracted}).
 *
 * <p>The meaning follows NeoForge {@code PlayerInteractEvent.EntityInteract}: it fires when a
 * player right-clicks an entity (attacks are excluded; in 26.1.2 an attack goes through the
 * separate {@code ServerboundAttackPacket}) and is dispatched by the <b>main hand item</b> at
 * the time of the interaction. On fabric, {@code FabricItemEventBindingsV2} +
 * {@code MixinServerGamePacketListenerItemInteract} convert the
 * {@code ServerPlayer.interactOn} call site in
 * {@code ServerGamePacketListenerImpl#handleInteract}, after the entity existence, world
 * border and interaction range checks have passed.
 *
 * <p>Cancel semantics: a listener {@code return true} drops this interaction (fabric cancels
 * the whole packet handling and runs no vanilla interaction; the NeoForge setCanceled
 * equivalent).
 */
@Doc("Fired when a player interacts (right-click) with an entity (ItemEvents.entityInteracted). Dispatched by the item in the main hand.")
@Doc("Cancellable: return true to cancel the interaction.")
@Getter
public class PlayerEntityInteractEventJS {

    @Doc("The player interacting.")
    private final Player player;

    @Doc("The entity being interacted with.")
    private final Entity target;

    @Doc("The item stack held in the interacting hand.")
    private final ItemStack item;

    @Doc("The interacting hand.")
    private final InteractionHand hand;

    public PlayerEntityInteractEventJS(Player player, Entity target, ItemStack item, InteractionHand hand) {
        this.player = player;
        this.target = target;
        this.item = item;
        this.hand = hand;
    }
}
