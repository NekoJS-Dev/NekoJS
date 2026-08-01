package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 玩家物品栏变化事件（{@code PlayerEvents.inventoryChanged}）。
 *
 * <p>1.12.2 版本：由挂在玩家 {@code inventoryContainer} 上的
 * {@code IContainerListener}（{@link com.tkisor.nekojs.listener.InventoryChangeListener}）
 * 在 {@code sendSlotContents} 回调时触发，按物品 id 分发（脚本可
 * {@code PlayerEvents.inventoryChanged('minecraft:stone', event => ...)}）。
 */
@Getter
public class InventoryChangedEventJS {
    private final EntityPlayer player;
    private final ItemStack item;
    private final int slot;

    public InventoryChangedEventJS(EntityPlayer player, ItemStack item, int slot) {
        this.player = player;
        this.item = item;
        this.slot = slot;
    }
}
