package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.ItemStackSpec;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.crafting.IngredientNBT;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 */
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec {

    // 1.12.2 NBT tag type ids（NBTBase 未公开这些常量，按 ID 直接用）。
    int TAG_LIST = 9;
    int TAG_STRING = 8;

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
    @Override
    default Object neko$withCount(int count) {
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
    @Override
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
    @Override
    default boolean neko$isUnbreakable() {
        return self().hasTagCompound() && self().getTagCompound().getBoolean("Unbreakable");
    }

    /**
     * 设置当前物品栈不可破坏状态。对齐 1.21.1 {@code setUnbreakable(boolean)}。
     * 1.12.2 写入/移除根 NBT tag {@code Unbreakable}。
     *
     * @param unbreakable 是否不可破坏
     */
    @Override
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
    @Override
    default boolean neko$matches(Object stack) {
        if (stack instanceof ItemStack) {
            return neko$matches((ItemStack) stack);
        }
        if (stack instanceof Item) {
            return neko$matches((Item) stack);
        }
        if (stack instanceof Ingredient) {
            return neko$matches((Ingredient) stack);
        }
        return false;
    }

    // ---- 跨平台对齐：NF 1.21.1 / 26 的可移植 extras（D13） ----
    // 下列方法对齐 NeoForge 平台 ItemStackExtension 的方法集，补齐此前 CR 缺失的脚本表面。
    // 不可移植的（ItemLore 类型的 getLore()/setLore(ItemLore)、ENCHANTMENT_GLINT_OVERRIDE 的
    // setEnchanted）保留为 NF-only：1.12.2 无对应 MC API。

    /**
     * 取物品栈对应的方块（仅 {@link ItemBlock} 返回非 null）。对齐 NF {@code neko$getBlock}。
     *
     * @return 方块；非方块物品返回 null
     */
    default Object neko$getBlock() {
        Item item = self().getItem();
        return item instanceof ItemBlock ? ((ItemBlock) item).getBlock() : null;
    }

    /**
     * 取物品所属 mod 的 namespace。对齐 NF {@code neko$getMod}。
     *
     * @return 注册 namespace，如 {@code "minecraft"} / {@code "nekojs"}
     */
    default String neko$getMod() {
        return ForgeRegistries.ITEMS.getKey(self().getItem()).getNamespace();
    }

    /**
     * 当前物品栈是否带附魔光辉。对齐 NF {@code neko$isEnchanted}。
     * 1.12.2 用 {@link ItemStack#hasEffect()}（附魔或药水效果均返回 true，与 NF glint 语义最接近）。
     *
     * @return {@code true} 若带 effect
     */
    default boolean neko$isEnchanted() {
        return self().hasEffect();
    }

    /**
     * 读取 lore 文本行（来自 {@code display.Lore} NBT）。对齐 NF {@code neko$getLore} 的脚本用途。
     * NF 返回 {@code ItemLore} 组件；CR 1.12.2 无此类型，这里返回文本组件列表
     * （1.12.2 lore 以转义 JSON 字符串存于 NBT）。
     *
     * @return lore 行；无 lore 返回空列表
     */
    default List<ITextComponent> neko$getLore() {
        NBTTagCompound display = displayTag();
        if (display == null || !display.hasKey("Lore", TAG_LIST)) {
            return Collections.emptyList();
        }
        NBTTagList list = display.getTagList("Lore", TAG_STRING);
        List<ITextComponent> lines = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            String json = list.getStringTagAt(i);
            ITextComponent line = json.isEmpty() ? new TextComponentString("")
                    : ITextComponent.Serializer.jsonToComponent(json);
            lines.add(line != null ? line : new TextComponentString(json));
        }
        return lines;
    }

    /**
     * 写入 lore 文本行（写入 {@code display.Lore} NBT）。对齐 NF {@code neko$setLore(List)}。
     * {@code neko$setLore(ItemLore)} 重载在 CR 上不存在（无 ItemLore 类型）。
     *
     * @param lines lore 行；null 或空列表清除 lore
     */
    default void neko$setLore(List<ITextComponent> lines) {
        NBTTagCompound display = displayTag();
        if (lines == null || lines.isEmpty()) {
            if (display != null) {
                display.removeTag("Lore");
                if (display.isEmpty()) {
                    removeDisplayTag();
                }
            }
            return;
        }
        if (display == null) {
            display = new NBTTagCompound();
            rootTag().setTag("display", display);
        }
        NBTTagList list = new NBTTagList();
        for (ITextComponent line : lines) {
            list.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(line)));
        }
        display.setTag("Lore", list);
    }

    /**
     * 转为配方 {@link Ingredient}。对齐 NF {@code neko$asIngredient}。
     * 1.12.2 ItemStack 原生携带 NBT，{@link Ingredient#fromStacks(ItemStack...)}
     * 按 NBT 匹配——即"严格 NBT"。
     *
     * @return 包含当前物品栈的 Ingredient
     */
    default Ingredient neko$asIngredient() {
        return neko$strictNBT();
    }

    /**
     * 弱 NBT 匹配 Ingredient：1.12.2 无原生"忽略 NBT"的 Ingredient，
     * 退化为 {@link Ingredient#fromStacks(ItemStack...)}（按 NBT 匹配）。
     * 对齐 NF {@code neko$weakNBT} 的方法名；语义偏差已注释。
     */
    default Ingredient neko$weakNBT() {
        return Ingredient.fromStacks(self());
    }

    /**
     * 严格 NBT 匹配 Ingredient：用 Forge {@link IngredientNBT}（NBT 敏感匹配）。
     * 对齐 NF {@code neko$strictNBT}。
     */
    default Ingredient neko$strictNBT() {
        return new StrictNbtIngredient(self());
    }

    /** 判断是否与另一物品栈物品+metadata 相同（忽略 NBT）。对齐 NF {@code neko$areItemsEqual}。 */
    default boolean neko$areItemsEqual(ItemStack stack) {
        return self().isItemEqual(stack);
    }

    /**
     * 判断是否与另一物品栈 NBT 相同。对齐 NF {@code neko$areComponentsEqual}。
     * 1.12.2 无"组件"概念，等价于 NBT 比较。
     */
    default boolean neko$areComponentsEqual(ItemStack stack) {
        return ItemStack.areItemStackTagsEqual(self(), stack);
    }

    /** 判断是否与另一物品栈物品+metadata+NBT 相同（忽略数量）。对齐 NF {@code neko$equalsIgnoringCount}。 */
    default boolean neko$equalsIgnoringCount(ItemStack stack) {
        if (self() == stack) return true;
        if (self().isEmpty()) return stack.isEmpty();
        return self().isItemEqual(stack) && ItemStack.areItemStackTagsEqual(self(), stack);
    }

    /** 类型化重载：物品栈完全匹配。对齐 NF {@code neko$matches(ItemStack)}。 */
    default boolean neko$matches(ItemStack stack) {
        return self().isItemEqual(stack) && ItemStack.areItemStackTagsEqual(self(), stack);
    }

    /** 类型化重载：物品匹配。对齐 NF {@code neko$matches(ItemLike)}（CR 用 {@link Item}）。 */
    default boolean neko$matches(Item item) {
        return self().getItem() == item;
    }

    /** 类型化重载：Ingredient 测试。对齐 NF {@code neko$matches(Ingredient)}。 */
    default boolean neko$matches(Ingredient ingredient) {
        return ingredient.apply(self());
    }

    // ---- NBT display/lore 访问 helper ----

    private NBTTagCompound rootTag() {
        NBTTagCompound tag = self().getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            self().setTagCompound(tag);
        }
        return tag;
    }

    private NBTTagCompound displayTag() {
        return self().hasTagCompound() ? self().getTagCompound().getCompoundTag("display") : null;
    }

    private void removeDisplayTag() {
        if (self().hasTagCompound()) {
            self().getTagCompound().removeTag("display");
        }
    }

    /**
     * 严格 NBT Ingredient：子类化 Forge {@link IngredientNBT}（构造函数 protected）。
     * 用于 {@link #neko$strictNBT()}。
     */
    class StrictNbtIngredient extends IngredientNBT {
        public StrictNbtIngredient(ItemStack stack) {
            super(stack);
        }
    }

    /**
     * 将 {@code "sharpness"} 归一化为 {@code "minecraft:sharpness"}。
     */
    private static String normalizeId(String id) {
        return id != null && id.contains(":") ? id : "minecraft:" + id;
    }
}
