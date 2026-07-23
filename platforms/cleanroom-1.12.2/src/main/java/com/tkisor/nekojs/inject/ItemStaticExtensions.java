package com.tkisor.nekojs.inject;

import com.tkisor.nekojs.api.annotation.StaticInjector;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import net.minecraft.item.ItemStack;

/**
 * cleanroom 1.12：{@code net.minecraft.item.Item} 的静态扩展载体（<b>非</b> mixin 类）。
 *
 * <p>带 {@link StaticInjector} 的方法会被 {@code NekoClassTransformer}（coremod IClassTransformer）
 * 在类加载时复制到 {@code net.minecraft.item.Item}，作为其 public static 方法，从而让
 * {@code Java.type('net.minecraft.item.Item').of('stone')} 可用——绕过 Mixin「禁止注入 public static」限制。
 *
 * <p>关键设计：{@code of} 方法体<b>只调用 nekojs 自身的 {@link ItemStackAdapter}</b>，不直接引用任何
 * MC API。这样复制到 Item 类后无 reobf/mapping 问题（nekojs 类引用在 dev 与生产 notch 环境下都解析到同包）。
 */
public final class ItemStaticExtensions {
    private ItemStaticExtensions() {}

    @StaticInjector("net.minecraft.item.Item")
    public static ItemStack of(String id) {
        return ItemStackAdapter.stringToItemStack(id);
    }

    @StaticInjector("net.minecraft.item.Item")
    public static ItemStack of(String id, int count) {
        return ItemStackAdapter.withCount(ItemStackAdapter.stringToItemStack(id), count);
    }
}
