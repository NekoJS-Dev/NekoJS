package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Listens for player inventory changes (modeled on KubeJS {@code KubeJSInventoryListener}).
 *
 * <p>Attached to the player's {@code inventoryMenu}, it dispatches slot changes by item id to
 * {@link PlayerEvents#INVENTORY_CHANGED}. {@link #getOrCreate(Player)} caches one instance per player
 * in a WeakHashMap that reclaims it once the player is unloaded, so no mixin is needed.
 */
public final class InventoryChangeListener implements ContainerListener {

    // WeakHashMap so entries are reclaimed when the player is GC'd; synchronized
    // because getOrCreate can be reached from multiple event paths.
    private static final Map<Player, InventoryChangeListener> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final Player player;

    private InventoryChangeListener(Player player) {
        this.player = player;
    }

    /** Returns the listener for a player, creating it and attaching it to inventoryMenu when it is missing. */
    public static InventoryChangeListener getOrCreate(Player player) {
        return CACHE.computeIfAbsent(player, p -> {
            InventoryChangeListener listener = new InventoryChangeListener(p);
            p.inventoryMenu.addSlotListener(listener);
            return listener;
        });
    }

    @Override
    public void slotChanged(AbstractContainerMenu container, int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        // Short-circuit when nothing listens: slot changes fire very often, so skip building and dispatching
        // the event object (the same approach ProbeCoordinator takes)
        if (!PlayerEvents.INVENTORY_CHANGED.hasListeners()) {
            return;
        }
        // React only to the player's own inventory slots (container == player.getInventory())
        if (container.getSlot(index).container != player.getInventory()) {
            return;
        }
        var key = stack.getItem();
        PlayerEvents.INVENTORY_CHANGED.post(
                new InventoryChangedEventJS(player, stack, container.getSlot(index).getContainerSlot()),
                key);
    }

    @Override
    public void dataChanged(AbstractContainerMenu container, int id, int value) {
        // Ignores data slot changes
    }
}
