package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 脚本侧的 Item 工厂与助手，同时绑定为全局 {@code Item} 与 {@code ItemJS}。
 *
 * <p>对齐 KubeJS 语义：{@code Item.of('minecraft:stone')} 返回 {@link ItemStack}。
 * 之所以用包装对象而非给 MC 的 {@code Item} 类注入静态方法，是因为 SpongePowered Mixin
 * 禁止注入 public static 方法，而 IMixinConfigPlugin 的 ASM 复制方案在 bootstrap 阶段会触发
 * {@code SymbolTable} NPE（见 memory: mixin-static-inject-bootstrap-npe）。
 */
public class ItemJS {

    /** KubeJS 风格：按字符串 id 创建物品栈，支持 {@code "1xminecraft:stone"} 计数语法。 */
    public ItemStack of(String id) {
        return ItemStackAdapter.stringToItemStack(id);
    }

    /** 按字符串 id + 数量创建物品栈。 */
    public ItemStack of(String id, int count) {
        return ItemStackAdapter.withCount(ItemStackAdapter.stringToItemStack(id), count);
    }

    /** 直接传入已存在的物品栈（原样返回，便于脚本统一走 {@code of} 入口）。 */
    public ItemStack of(ItemStack stack) {
        return stack;
    }

    /** 传入物品栈并指定数量。 */
    public ItemStack of(ItemStack stack, int count) {
        return ItemStackAdapter.withCount(stack, count);
    }

    /** 空物品栈。 */
    public ItemStack empty() {
        return ItemStack.EMPTY;
    }

    /** 按字符串 id 查物品；不存在返回 null。id 缺省 {@code minecraft:} 前缀。 */
    public Item id(String id) {
        Identifier location = ParseIds.parseItemOrBlockId(id);
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    /** 取物品的注册 id（{@code minecraft:stone} 形式）。 */
    public Identifier idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
