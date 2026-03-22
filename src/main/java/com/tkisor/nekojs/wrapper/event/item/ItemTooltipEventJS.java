package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.wrapper.entity.client.LocalPlayerWrapper;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public class ItemTooltipEventJS {

    private final ItemTooltipEvent rawEvent;

    public ItemTooltipEventJS(ItemTooltipEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public ItemStackWrapper getItem() {
        return new ItemStackWrapper(rawEvent.getItemStack());
    }

    public String getItemId() {
        return BuiltInRegistries.ITEM.getKey(rawEvent.getItemStack().getItem()).toString();
    }

    /**
     * 获取当前查看提示框的玩家
     * JS 侧调用: event.player
     */
    public LocalPlayerWrapper getPlayer() {
        Player player = rawEvent.getEntity();
        return player != null ? new LocalPlayerWrapper((LocalPlayer) player) : null;
    }

    public void add(Component text) {
        rawEvent.getToolTip().add(text);
    }

    public void insert(int index, Component text) {
        List<Component> tooltip = rawEvent.getToolTip();
        if (index >= 0 && index <= tooltip.size()) {
            tooltip.add(index, text);
        } else {
            add(text);
        }
    }

    /**
     * 删除指定行号的文本
     * JS 侧调用: event.remove(1)
     */
    public boolean remove(int index) {
        List<Component> tooltip = rawEvent.getToolTip();
        if (index >= 0 && index < tooltip.size()) {
            tooltip.remove(index);
            return true;
        }
        return false;
    }

    /**
     * 清空所有提示文本（甚至连物品名字都删了）
     * JS 侧调用: event.clear()
     */
    public void clear() {
        rawEvent.getToolTip().clear();
    }

    public boolean isAdvanced() {
        return rawEvent.getFlags().isAdvanced();
    }
}