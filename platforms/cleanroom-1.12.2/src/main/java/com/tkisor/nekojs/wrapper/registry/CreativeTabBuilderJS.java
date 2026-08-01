package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * 1.12.2 创造标签页注册器（{@code StartupEvents.registry('creativeModeTab')}）。
 *
 * <p>1.12.2 的 {@link CreativeTabs} 不是注册表（无 {@code RegistryEvent.Register}），
 * 实例构造即加入静态 {@code CREATIVE_TAB_ARRAY}。因此在 BLOCK 分支开头（同 FLUID 模式）
 * 构造即可；构造出的实例可传给 {@code ItemBuilderJS.creativeTab(...)}。
 *
 * <p>注意：{@code createIcon()} 由创造 GUI 懒调用，可能晚于 ITEM 注册，
 * 故图标用 {@link ItemStack} 引用（可引用稍后才注册的物品实例）。
 */
public class CreativeTabBuilderJS {

    private final String label;
    private ItemStack icon;
    private final NonNullList<ItemStack> items = NonNullList.create();

    public CreativeTabBuilderJS(String label) {
        this.label = label;
    }

    /** 图标（物品 id 字符串或 ItemStack）。 */
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

    /**
     * 构造并加入静态标签页数组。{@code createIcon} 返回图标栈；
     * {@code displayAllRelevantItems} 输出 builder 收集的条目。
     */
    @SuppressWarnings("deprecation")
    public CreativeTabs build() {
        ItemStack iconStack = icon != null ? icon : new ItemStack(Items.STICK);
        return new CreativeTabs(label) {
            @Override
            public ItemStack createIcon() {
                return iconStack;
            }

            @Override
            public void displayAllRelevantItems(NonNullList<ItemStack> list) {
                list.addAll(items);
            }
        };
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
