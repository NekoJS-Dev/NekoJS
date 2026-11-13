package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the player crafted / smelted event
 * ({@code PlayerEvents.crafted / smelted}, both dispatched by item id).
 *
 * <p>On NeoForge these correspond to the native {@code PlayerEvent.ItemCraftedEvent}
 * (getCrafting/getInventory) and {@code PlayerEvent.ItemSmeltedEvent}
 * (getSmelting/getAmountRemoved). This payload uses {@code result} (the produced stack:
 * crafting for crafted, smelting for smelted) and a nullable {@code container} (for crafted,
 * the crafting input container {@code CraftingContainer}; smelted has no such member and gets
 * null).
 *
 * <p>NeoForge 26.x fires these inside {@code ResultSlot#checkTakeAchievements} (crafted) and
 * {@code FurnaceResultSlot#checkTakeAchievements} (smelted), and the fabric hook matches them.
 * They only cover taking the result from a crafting table (including the portable one) and
 * from a furnace, blast furnace or smoker.
 */
@Doc("Fired when a player takes a crafted or smelted item (PlayerEvents.crafted / smelted, dispatched by item id).")
@Doc("event.result is the produced stack; event.container (crafted only) is the crafting input container, null for smelted.")
@Getter
public class PlayerCraftedEventJS {

    @Doc("The player who crafted or smelted the item.")
    private final ServerPlayer player;

    @Doc("The produced item stack (crafting output / furnace output).")
    private final ItemStack result;

    @Doc("The crafting input container (crafted only); null for smelted (furnace has no equivalent member).")
    private final Container container;

    public PlayerCraftedEventJS(ServerPlayer player, ItemStack result, Container container) {
        this.player = player;
        this.result = result;
        this.container = container;
    }
}
