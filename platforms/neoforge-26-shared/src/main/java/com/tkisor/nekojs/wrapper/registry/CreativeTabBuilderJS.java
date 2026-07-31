package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 创造模式标签页注册器（{@code StartupEvents.registry('creativeModeTab')}）。
 *
 * <p>脚本通过 {@code create(id)} 定义标签页：标题、图标与条目；条目在注册时
 * 快照到标签页（注册后新增条目需重新注册/重进存档）。
 */
public class CreativeTabBuilderJS {
    @Getter
    private final Identifier location;

    private Component title = Component.literal("NekoJS");
    private ItemStack icon = new ItemStack(Items.BARRIER);
    private final List<ItemStack> items = new ArrayList<>();

    public CreativeTabBuilderJS(Identifier location) {
        this.location = location;
    }

    /** 标签页标题。 */
    public CreativeTabBuilderJS title(String title) {
        this.title = Component.literal(title);
        return this;
    }

    /** 标签页图标（物品 id 字符串或 ItemStack）。 */
    public CreativeTabBuilderJS icon(Object icon) {
        ItemStack stack = resolveStack(icon);
        if (stack != null) {
            this.icon = stack;
        }
        return this;
    }

    /** 添加条目（物品 id 字符串或 ItemStack）。 */
    public CreativeTabBuilderJS add(Object item) {
        ItemStack stack = resolveStack(item);
        if (stack != null) {
            items.add(stack);
        }
        return this;
    }

    public CreativeModeTab build() {
        return CreativeModeTab.builder()
                .title(title)
                .icon(() -> icon)
                .displayItems((parameters, output) -> output.acceptAll(items))
                .build();
    }

    private static ItemStack resolveStack(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        if (value instanceof String id) {
            return ItemStackAdapter.stringToItemStack(id);
        }
        return null;
    }
}
