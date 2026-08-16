package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家物品栏变化事件（{@code PlayerEvents.inventoryChanged}）。
 *
 * <p>由挂在玩家 {@code inventoryMenu} 上的 {@code ContainerListener} 在槽位变化时触发，
 * 按物品 id 分发（脚本可 {@code PlayerEvents.inventoryChanged('minecraft:stone', event => ...)}）。
 */
@Doc("Event fired when a slot in a player's inventory changes (PlayerEvents.inventoryChanged).")
@Doc("Listeners are dispatched by item id, e.g. PlayerEvents.inventoryChanged('minecraft:stone', event => ...).")
@Getter
public class InventoryChangedEventJS {
    @Doc("The player whose inventory changed.")
    private final Player player;

    @Doc("The item stack now in the changed slot (may be empty when the slot was cleared).")
    private final ItemStack item;

    @Doc("Index of the changed slot in the player's inventory menu.")
    private final int slot;

    public InventoryChangedEventJS(Player player, ItemStack item, int slot) {
        this.player = player;
        this.item = item;
        this.slot = slot;
    }
}
