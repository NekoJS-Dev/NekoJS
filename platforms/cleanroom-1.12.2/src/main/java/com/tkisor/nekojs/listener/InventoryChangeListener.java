package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.WeakHashMap;

/**
 * 玩家物品栏变化监听器（仿 KubeJS {@code KubeJSInventoryListener}，1.12.2 适配）。
 *
 * <p>挂在玩家 {@code inventoryContainer} 上，槽位变化时按物品 id 分发到
 * {@link PlayerEvents#INVENTORY_CHANGED}。实例由 {@link #getOrCreate(EntityPlayer)}
 * 按玩家缓存（WeakHashMap，玩家卸载后自动回收），无需 mixin。
 *
 * <p>1.12.2 的 {@link IContainerListener} 没有 {@code slotChanged} 回调；槽位变化信号走
 * {@link #sendSlotContents}。注意 1.12.2 中 {@link EntityPlayerMP} 自身已实现
 * {@code IContainerListener} 并挂载在每个容器上，本监听器是叠加挂载（不替换）。
 */
public final class InventoryChangeListener implements IContainerListener {

    private static final WeakHashMap<EntityPlayer, InventoryChangeListener> CACHE = new WeakHashMap<>();

    private final EntityPlayer player;

    private InventoryChangeListener(EntityPlayer player) {
        this.player = player;
    }

    /** 取（或创建并挂载到 inventoryContainer）玩家的监听器实例。 */
    public static InventoryChangeListener getOrCreate(EntityPlayer player) {
        return CACHE.computeIfAbsent(player, p -> {
            InventoryChangeListener listener = new InventoryChangeListener(p);
            p.inventoryContainer.addListener(listener);
            return listener;
        });
    }

    @Override
    public void sendSlotContents(Container container, int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (index < 0 || index >= container.inventorySlots.size()) {
            return;
        }
        Slot slot = container.getSlot(index);
        // 仅响应玩家自身物品栏槽位（slot.inventory == player.inventory）
        IInventory slotInv = slot.inventory;
        if (slotInv != player.inventory) {
            return;
        }
        var key = stack.getItem();
        PlayerEvents.INVENTORY_CHANGED.post(
                new InventoryChangedEventJS(player, stack, slot.getSlotIndex()),
                key);
    }

    @Override
    public void sendAllContents(Container containerToSend, NonNullList<ItemStack> itemsList) {
        // 不关心全量同步
    }

    @Override
    public void sendWindowProperty(Container containerIn, int varToUpdateIndex, int newValue) {
        // 不关心数据条变化
    }

    @Override
    public void sendAllWindowProperties(Container containerIn, IInventory inventory) {
        // 不关心数据条变化
    }
}
