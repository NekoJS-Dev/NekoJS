package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public class ItemTooltipEventJS implements NekoEvent {

    private final ItemTooltipEvent rawEvent;

    public ItemTooltipEventJS(ItemTooltipEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    /**
     * 获取原始的 ItemStack
     * JS 侧调用: event.item 或 event.getItem()
     */
    public ItemStackWrapper getItem() {
        return new ItemStackWrapper(rawEvent.getItemStack());
    }

    /**
     * 快捷获取物品的标准命名空间 ID (例如 "minecraft:apple")
     * JS 侧调用: event.itemId
     */
    public String getItemId() {
        return BuiltInRegistries.ITEM.getKey(rawEvent.getItemStack().getItem()).toString();
    }

    /**
     * 在提示框末尾追加一行文本
     * JS 侧调用: event.add("这是一行红字")
     */
    public void add(String text) {
        rawEvent.getToolTip().add(Component.literal(text));
    }

    /**
     * 在指定的行号插入文本 (0 是物品名称)
     * JS 侧调用: event.insert(1, "插入到名称下面")
     */
    public void insert(int index, String text) {
        List<Component> tooltip = rawEvent.getToolTip();
        if (index >= 0 && index <= tooltip.size()) {
            tooltip.add(index, Component.literal(text));
        } else {
            add(text); // 越界则追加到末尾
        }
    }

    /**
     * 判断玩家是否开启了高级提示框 (F3 + H)
     * JS 侧调用: event.isAdvanced()
     */
    public boolean isAdvanced() {
        return rawEvent.getFlags().isAdvanced();
    }
}