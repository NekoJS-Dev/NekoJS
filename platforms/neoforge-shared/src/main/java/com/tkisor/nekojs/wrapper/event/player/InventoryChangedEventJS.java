package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家物品栏变化事件（{@code PlayerEvents.inventoryChanged}）。
 *
 * <p>由挂在玩家 {@code inventoryMenu} 上的 {@code ContainerListener} 在槽位变化时触发，
 * 按物品 id 分发（脚本可 {@code PlayerEvents.inventoryChanged('minecraft:stone', event => ...)}）。
 */
@Getter
public class InventoryChangedEventJS {
    private final Player player;
    private final ItemStack item;
    private final int slot;

    public InventoryChangedEventJS(Player player, ItemStack item, int slot) {
        this.player = player;
        this.item = item;
        this.slot = slot;
    }
}
