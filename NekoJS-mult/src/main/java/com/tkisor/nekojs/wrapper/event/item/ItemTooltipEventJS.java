package com.tkisor.nekojs.wrapper.event.item;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品 tooltip 事件的中立 payload（成员 {@code event.itemStack} / {@code event.lines}）。
 * {@code lines} 是渲染前可变的 tooltip 行列表——监听器直接增删改其中的
 * {@link Component}；fabric 桥投递的是原列表（mutate 即生效，不可取消整段渲染）。
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
