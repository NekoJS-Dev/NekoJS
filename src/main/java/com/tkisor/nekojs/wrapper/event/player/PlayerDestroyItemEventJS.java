package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the player item broken event ({@code PlayerEvents.destroyed},
 * dispatched by item id).
 *
 * <p>On NeoForge it corresponds to the native {@code PlayerDestroyItemEvent} (getOriginal is a
 * copy of the stack before it broke). This payload uses {@code itemStack}: the stack at the
 * break point (the breaking branch of {@code ItemStack#applyDamage}), with the damage already
 * capped and the count as it was before leaving the slot. The count can be greater than 1, and
 * the event fires once per broken item.
 *
 * <p>The fabric hook matches NeoForge's full coverage: every item that loses durability through
 * {@code ItemStack#hurtAndBreak} (block breaking, attacking, item use, shield blocking, fishing
 * rod) fires when it actually breaks. Non-player holders (such as a tool in a zombie's hand)
 * and infinite materials are filtered out along the way.
 */
@Doc("Fired when a player's item breaks from durability damage (PlayerEvents.destroyed, dispatched by item id).")
@Doc("event.itemStack is the broken stack at the break point (damage capped, count still before the -1 of the broken item).")
@Getter
public class PlayerDestroyItemEventJS {

    @Doc("The player whose item broke.")
    private final ServerPlayer player;

    @Doc("The item stack that broke just before the broken item is removed from it.")
    private final ItemStack itemStack;

    public PlayerDestroyItemEventJS(ServerPlayer player, ItemStack itemStack) {
        this.player = player;
        this.itemStack = itemStack;
    }
}
