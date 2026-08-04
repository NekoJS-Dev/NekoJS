package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1.12.2 {@link ItemStack} 统一扩展方法，注入到 MC 的 {@link ItemStack} 类。
 *
 * <p>1.12.2 与 1.21.1 的关键差异：
 * <ul>
 *   <li>没有 DataComponents，物品数据完全由 NBT + metadata 承载</li>
 *   <li>附魔直接是 {@link Enchantment} 类，按 {@link Enchantment#getEnchantmentByLocation(String)} 查找</li>
 *   <li>不可破坏是 NBT tag {@code Unbreakable}（byte 1），不是 component</li>
 *   <li>物品匹配用 {@link ItemStack#isItemEqual(ItemStack)} + {@link ItemStack#areItemStackTagsEqual(ItemStack, ItemStack)}</li>
 * </ul>
 *
 * @see ItemStack
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface ItemStackExtension {

    private ItemStack self() {
        return (ItemStack) (Object) this;
    }

    /**
     * 取物品的注册 id（{@code minecraft:stone} 形式）。
     * 1.12.2 用 {@link ForgeRegistries#ITEMS}，对齐 1.21.1 的 {@code BuiltInRegistries.ITEM.getKey}。
     *
     * @return 物品 id；未注册返回 null
     */
    default String neko$getId() {
        return ForgeRegistries.ITEMS.getKey(self().getItem()).toString();
    }

    /**
     * 返回指定数量的当前物品栈副本。空栈或数量 ≤ 0 返回 {@link ItemStack#EMPTY}。
     * 对齐 1.21.1 {@code ItemStack.withCount(int)}。
     *
     * @param count 目标数量
     * @return 新的物品栈
     */
    default ItemStack neko$withCount(int count) {
        if (count <= 0 || self().isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = self().copy();
        copy.setCount(count);
        return copy;
    }

    /**
     * 复制当前物品栈（深拷贝，包含 NBT）。对齐 1.21.1 {@code ItemStack.copy()}。
     *
     * @return 物品栈副本
     */
    default ItemStack neko$copy() {
        return self().copy();
    }

    /**
     * 设置数量并返回自身（builder 风格）。对齐 1.21.1 {@code setCount(int)} 的链式语义。
     *
     * @param count 目标数量
     * @return 当前物品栈
     */
    default ItemStack neko$setCount(int count) {
        self().setCount(count);
        return self();
    }

    /**
     * 取物品栈对应的 {@link Item}。对齐 1.21.1 {@code getItem()}。
     *
     * @return 物品
     */
    default Item neko$getItem() {
        return self().getItem();
    }

    /**
     * 按字符串 id 附魔。对齐 1.21.1 {@code enchant(String, int)}。
     * 1.12.2 用 {@link Enchantment#getEnchantmentByLocation(String)} 查找 {@link Enchantment}，
     * 然后 {@link ItemStack#addEnchantment(Enchantment, int)}。
     *
     * @param id   附魔 id，如 {@code "minecraft:sharpness"} 或 {@code "sharpness"}
     * @param level 附魔等级，必须为正
     * @return 当前物品栈
     */
    default ItemStack neko$enchant(String id, int level) {
        if (level <= 0) {
            throw new IllegalArgumentException("Enchantment level must be positive: " + level);
        }
        Enchantment ench = Enchantment.getEnchantmentByLocation(normalizeId(id));
        if (ench == null) {
            throw new IllegalArgumentException("Invalid enchantment id: " + id);
        }
        self().addEnchantment(ench, level);
        return self();
    }

    /**
     * 判断当前物品栈是否拥有指定等级及以上的附魔。对齐 1.21.1 {@code hasEnchantment(String, int)}。
     * 1.12.2 用 {@link EnchantmentHelper#getEnchantmentLevel(Enchantment, ItemStack)}。
     *
     * @param id    附魔 id
     * @param level 期望最低等级
     * @return {@code true} 若附魔等级 ≥ level
     */
    default boolean neko$hasEnchantment(String id, int level) {
        Enchantment ench = Enchantment.getEnchantmentByLocation(normalizeId(id));
        if (ench == null) return false;
        return EnchantmentHelper.getEnchantmentLevel(ench, self()) >= level;
    }

    /**
     * 当前物品栈是否不可破坏。
     * 1.12.2 的不可破坏标志存在根 NBT tag {@code Unbreakable}（byte），没有 component。
     *
     * @return {@code true} 若 Unbreakable tag 为真
     */
    default boolean neko$isUnbreakable() {
        return self().hasTagCompound() && self().getTagCompound().getBoolean("Unbreakable");
    }

    /**
     * 设置当前物品栈不可破坏状态。对齐 1.21.1 {@code setUnbreakable(boolean)}。
     * 1.12.2 写入/移除根 NBT tag {@code Unbreakable}。
     *
     * @param unbreakable 是否不可破坏
     */
    default void neko$setUnbreakable(boolean unbreakable) {
        NBTTagCompound tag = self().getTagCompound();
        if (unbreakable) {
            if (tag == null) {
                tag = new NBTTagCompound();
                self().setTagCompound(tag);
            }
            tag.setBoolean("Unbreakable", true);
        } else if (tag != null) {
            tag.removeTag("Unbreakable");
        }
    }

    /**
     * 判断当前物品栈是否与给定物品栈完全匹配（item + metadata + NBT）。
     * 对齐 1.21.1 {@code matches(ItemStack)}（{@code ItemStack.isSameItemSameComponents}）。
     * 1.12.2 等价于 {@link ItemStack#isItemEqual(ItemStack)} &&
     * {@link ItemStack#areItemStackTagsEqual(ItemStack, ItemStack)}。
     *
     * @param stack 另一个物品栈
     * @return {@code true} 若物品 + metadata + NBT 完全一致
     */
    default boolean neko$matches(ItemStack stack) {
        if (stack == null) return false;
        return self().isItemEqual(stack) && ItemStack.areItemStackTagsEqual(self(), stack);
    }

    /**
     * 将 {@code "sharpness"} 归一化为 {@code "minecraft:sharpness"}。
     */
    private static String normalizeId(String id) {
        return id != null && id.contains(":") ? id : "minecraft:" + id;
    }
}
