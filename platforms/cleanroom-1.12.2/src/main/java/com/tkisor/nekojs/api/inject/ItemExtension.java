package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1.12.2 {@link Item} 统一扩展方法，注入到 MC 的 {@link Item} 类。
 *
 * <p>1.12.2 注册表用 {@link ForgeRegistries#ITEMS}（对齐 1.21.1 的 {@code BuiltInRegistries.ITEM}）。
 *
 * @see Item
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface ItemExtension {

    private Item self() {
        return (Item) this;
    }

    /**
     * 取物品的注册 id（{@code minecraft:stone} 形式）。
     * 1.12.2 用 {@link ForgeRegistries#ITEMS}。
     *
     * @return 物品 id；未注册返回 null
     */
    default String neko$getId() {
        return ForgeRegistries.ITEMS.getKey(self()).toString();
    }
}
