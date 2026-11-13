package com.tkisor.nekojs.wrapper.event.item;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Loader-neutral payload for the item tooltip event (members {@code event.itemStack} /
 * {@code event.lines}). {@code lines} is the mutable list of tooltip lines before rendering,
 * so a listener adds, removes or replaces the {@link Component}s directly. The fabric bridge
 * passes the original list (mutations take effect; it cannot cancel the whole rendering).
 */
public class ItemTooltipEventJS {

    @Getter
    private final ItemStack itemStack;

    @Getter
    private final List<Component> lines;

    public ItemTooltipEventJS(ItemStack itemStack, List<Component> lines) {
        this.itemStack = itemStack;
        this.lines = lines;
    }
}
