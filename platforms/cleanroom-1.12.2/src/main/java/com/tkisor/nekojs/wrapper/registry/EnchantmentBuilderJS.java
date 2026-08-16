package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * 1.12.2 附魔注册器（{@code StartupEvents.registry('enchantment')}）。
 *
 * <p>1.12.2 的 {@link Enchantment} 构造器 protected、无抽象方法（默认
 * {@code getMaxLevel()=1}、{@code getMinEnchantability(level)=1+level*10}），
 * 用匿名子类实例化并可选覆盖等级曲线。注册到 {@code ForgeRegistries.ENCHANTMENTS}。
 */
@Doc("Builder for registering a new enchantment; obtain it from RegistryEvents.enchantment.create(id).")
public class EnchantmentBuilderJS {

    private final String registryName;
    private Enchantment.Rarity rarity = Enchantment.Rarity.COMMON;
    private EnumEnchantmentType type = EnumEnchantmentType.ALL;
    private EntityEquipmentSlot[] slots = new EntityEquipmentSlot[] { EntityEquipmentSlot.MAINHAND };
    private int maxLevel = 1;
    private int minEnchantabilityBase = -1;   // <0 表示用默认 1 + level * 10
    private int maxEnchantabilityBase = -1;   // <0 表示用默认 min + 5

    public EnchantmentBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 稀有度（'common' | 'uncommon' | 'rare' | 'very_rare'）。 */
    @Doc("Sets the enchantment rarity.")
    @Param(name = "rarityStr", value = "one of 'common', 'uncommon', 'rare', 'very_rare'; unknown names fall back to common")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS rarity(String rarityStr) {
        this.rarity = switch (rarityStr.toLowerCase()) {
            case "uncommon" -> Enchantment.Rarity.UNCOMMON;
            case "rare" -> Enchantment.Rarity.RARE;
            case "very_rare", "veryrare" -> Enchantment.Rarity.VERY_RARE;
            default -> Enchantment.Rarity.COMMON;
        };
        return this;
    }

    /** 可附魔类型（'all' | 'weapon' | 'armor' | 'digger' | 'bow' 等）。 */
    @Doc("Sets which item types the enchantment applies to.")
    @Param(name = "typeStr", value = "one of 'all', 'armor', 'armor_feet', 'armor_legs', 'armor_chest', 'armor_head', 'weapon', 'digger', 'fishing_rod', 'breakable', 'bow', 'wearable'")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS type(String typeStr) {
        this.type = switch (typeStr.toLowerCase()) {
            case "armor" -> EnumEnchantmentType.ARMOR;
            case "armor_feet", "feet" -> EnumEnchantmentType.ARMOR_FEET;
            case "armor_legs", "legs" -> EnumEnchantmentType.ARMOR_LEGS;
            case "armor_chest", "chest" -> EnumEnchantmentType.ARMOR_CHEST;
            case "armor_head", "head" -> EnumEnchantmentType.ARMOR_HEAD;
            case "weapon" -> EnumEnchantmentType.WEAPON;
            case "digger", "tool" -> EnumEnchantmentType.DIGGER;
            case "fishing_rod", "fishing" -> EnumEnchantmentType.FISHING_ROD;
            case "breakable" -> EnumEnchantmentType.BREAKABLE;
            case "bow" -> EnumEnchantmentType.BOW;
            case "wearable" -> EnumEnchantmentType.WEARABLE;
            default -> EnumEnchantmentType.ALL;
        };
        return this;
    }

    /** 生效槽位（'mainhand' | 'offhand' | 'head' | 'chest' | 'legs' | 'feet'）。 */
    @Doc("Sets the equipment slot the enchantment is effective on.")
    @Param(name = "slotStr", value = "one of 'mainhand', 'offhand', 'head', 'chest', 'legs', 'feet'")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS slot(String slotStr) {
        this.slots = switch (slotStr.toLowerCase()) {
            case "offhand" -> new EntityEquipmentSlot[] { EntityEquipmentSlot.OFFHAND };
            case "head" -> new EntityEquipmentSlot[] { EntityEquipmentSlot.HEAD };
            case "chest" -> new EntityEquipmentSlot[] { EntityEquipmentSlot.CHEST };
            case "legs" -> new EntityEquipmentSlot[] { EntityEquipmentSlot.LEGS };
            case "feet" -> new EntityEquipmentSlot[] { EntityEquipmentSlot.FEET };
            default -> new EntityEquipmentSlot[] { EntityEquipmentSlot.MAINHAND };
        };
        return this;
    }

    /** 最大附魔等级。 */
    @Doc("Sets the maximum enchantment level.")
    @Param(name = "maxLevel", value = "the maximum level; at least 1")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS maxLevel(int maxLevel) {
        this.maxLevel = Math.max(1, maxLevel);
        return this;
    }

    /** 附魔台最小需求等级曲线（level 1 时的值；默认 1 + level*10）。 */
    @Doc("Sets the base minimum enchantability required (level-1 value; default curve is 1 + level*10).")
    @Param(name = "base", value = "minimum enchantability base at level 1")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS minEnchantability(int base) {
        this.minEnchantabilityBase = base;
        return this;
    }

    /** 附魔台最大需求等级曲线（level 1 时的值；默认 min + 5）。 */
    @Doc("Sets the base maximum enchantability (level-1 value; default curve is base + level*10).")
    @Param(name = "base", value = "maximum enchantability base at level 1")
    @Return("this builder, for chaining")
    public EnchantmentBuilderJS maxEnchantability(int base) {
        this.maxEnchantabilityBase = base;
        return this;
    }

    /** 注册名。 */
    @Doc("Gets the registry name of the enchantment being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }

    /** 构建附魔实例（匿名子类）。 */
    @Doc("Builds the enchantment as an anonymous Enchantment subclass; registration happens when the event completes.")
    @Return("the configured enchantment")
    @SuppressWarnings("deprecation")
    public Enchantment build() {
        int minBase = minEnchantabilityBase;
        int maxBase = maxEnchantabilityBase;
        return new Enchantment(rarity, type, slots) {
            @Override
            public int getMaxLevel() {
                return maxLevel;
            }

            @Override
            public int getMinEnchantability(int level) {
                return minBase >= 0 ? minBase + level * 10 : super.getMinEnchantability(level);
            }

            @Override
            public int getMaxEnchantability(int level) {
                return maxBase >= 0 ? maxBase + level * 10 : super.getMaxEnchantability(level);
            }

            @Override
            public boolean canApply(ItemStack stack) {
                return type.canEnchantItem(stack.getItem()) || super.canApply(stack);
            }
        };
    }
}
