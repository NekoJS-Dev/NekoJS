package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
    @Doc("Creates an item stack from an item id string.")
    @Param(name = "id", value = "item id like 'minecraft:stone'; supports '1xminecraft:stone' count syntax")
    @Return("a new item stack with the parsed count, or the empty stack for empty id strings")
    public ItemStack of(String id) {
        return ItemStackAdapter.stringToItemStack(id);
    }

    /** 按字符串 id + 数量创建物品栈。 */
    @Doc("Creates an item stack from an item id string with an explicit count.")
    @Param(name = "id", value = "item id like 'minecraft:stone'")
    @Param(name = "count", value = "stack size, must be positive")
    @Return("a new item stack of the given size")
    public ItemStack of(String id, int count) {
        return ItemStackAdapter.withCount(ItemStackAdapter.stringToItemStack(id), count);
    }

    /** 直接传入已存在的物品栈（原样返回，便于脚本统一走 {@code of} 入口）。 */
    @Doc("Pass-through overload so scripts can funnel every stack through of().")
    @Param(name = "stack", value = "an existing item stack")
    @Return("the same stack, unchanged")
    public ItemStack of(ItemStack stack) {
        return stack;
    }

    /** 传入物品栈并指定数量。 */
    @Doc("Returns a copy of the given item stack with a new count.")
    @Param(name = "stack", value = "an existing item stack")
    @Param(name = "count", value = "stack size, must be positive")
    @Return("a copy of the stack with the given count; the empty stack if the input is empty")
    public ItemStack of(ItemStack stack, int count) {
        return ItemStackAdapter.withCount(stack, count);
    }

    /** 空物品栈。 */
    @Doc("Returns the shared empty item stack.")
    @Return("ItemStack.EMPTY, never null")
    public ItemStack empty() {
        return ItemStack.EMPTY;
    }

    /** 按字符串 id 查物品；不存在返回 null。对齐 NeoForge {@code Item.id(...)}。 */
    @Doc("Looks up a registered item by id.")
    @Param(name = "id", value = "item id like 'minecraft:stone'")
    @Return("the registered item, or null if the id is invalid or unregistered")
    public Item id(String id) {
        ResourceLocation location = tryParse(id);
        if (location == null) {
            return null;
        }
        return ForgeRegistries.ITEMS.getValue(location);
    }

    /** 取物品的注册 id（{@code minecraft:stone} 形式）；未注册返回 null。 */
    @Doc("Gets the registry id of an item.")
    @Param(name = "item", value = "a registered item")
    @Return("the id like 'minecraft:stone', or null if the item is not registered")
    public ResourceLocation idOf(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static ResourceLocation tryParse(String value) {
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

