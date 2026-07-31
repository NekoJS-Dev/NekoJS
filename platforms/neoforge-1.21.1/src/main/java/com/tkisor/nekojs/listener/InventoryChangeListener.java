package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;

import java.util.WeakHashMap;

/**
 * 玩家物品栏变化监听器（仿 KubeJS {@code KubeJSInventoryListener}）。
 *
 * <p>挂在玩家 {@code inventoryMenu} 上，槽位变化时按物品 id 分发到
 * {@link PlayerEvents#INVENTORY_CHANGED}。实例由 {@link #getOrCreate(Player)}
 * 按玩家缓存（WeakHashMap，玩家卸载后自动回收），无需 mixin。
 */
public final class InventoryChangeListener implements ContainerListener {

    private static final WeakHashMap<Player, InventoryChangeListener> CACHE = new WeakHashMap<>();

    private final Player player;

    private InventoryChangeListener(Player player) {
        this.player = player;
    }

    /** 取（或创建并挂载到 inventoryMenu）玩家的监听器实例。 */
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
        // 仅响应玩家自身物品栏槽位（container == player.getInventory()）
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
        // 不关心数据条变化
    }
}
