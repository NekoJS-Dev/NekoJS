package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.world.Container;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ItemCraftedEventJS implements NekoEvent {

    private final PlayerEvent.ItemCraftedEvent rawEvent;

    public ItemCraftedEventJS(PlayerEvent.ItemCraftedEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    /**
     * 获取合成该物品的玩家
     * JS 侧: event.player
     */
    public PlayerWrapper getPlayer() {
        return new PlayerWrapper(rawEvent.getEntity());
    }

    /**
     * 获取合成出来的物品堆 (可以修改数量或 NBT)
     * JS 侧: event.item
     */
    public ItemStackWrapper getItem() {
        return new ItemStackWrapper(rawEvent.getCrafting());
    }

    /**
     * 获取合成网格的容器 (用于读取消耗的材料)
     * JS 侧: event.inventory
     */
    public Container getInventory() {
        return rawEvent.getInventory();
    }
}