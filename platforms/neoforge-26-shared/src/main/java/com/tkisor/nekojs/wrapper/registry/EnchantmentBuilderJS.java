package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 附魔注册器（{@code StartupEvents.registry('enchantment')}）。
 *
 * <p>1.21+ 的 {@link Enchantment} 已改为 record，由 {@link Enchantment.EnchantmentDefinition}
 * 描述属性。脚本仅提供基础数值与一个物品标签 id（决定可附魔物品集合），由 {@link #create(RegisterEvent)}
 * 解析 item 注册表为 {@link HolderSet} 并经 {@link Enchantment.Builder#build(Identifier)} 组装。
 *
 * <p>默认不挂 {@code effects}（注册成功、可被附魔/书本/互斥，但无实际效果）；脚本无需提供
 * 复杂的运行时对象即可得到一个合法、可注册的附魔。
 */
public class EnchantmentBuilderJS implements TaggableBuilder<EnchantmentBuilderJS> {
    @Getter
    private final Identifier location;

    /** 支持附魔的物品标签 id（如 {@code '#minecraft:enchantable/weapon'}）。默认空集合。 */
    private String supportedItemsTag;
    private int weight = 1;
    private int maxLevel = 1;
    private int minCostBase = 1;
    private int minCostPerLevel = 0;
    private int maxCostBase = 1;
    private int maxCostPerLevel = 0;
    private int anvilCost = 0;
    private EquipmentSlotGroup slots = EquipmentSlotGroup.MAINHAND;

    public EnchantmentBuilderJS(Identifier location) {
        this.location = location;
    }

    /** {@link TaggableBuilder}：附魔 tag（如 {@code minecraft:treasure}）归属 ENCHANTMENT 注册表。 */
    @Override
    public ResourceKey<? extends Registry<?>> getTagRegistry() {
        return Registries.ENCHANTMENT;
    }

    /** 支持附魔的物品标签（带或不带 {@code #} 前缀均可）。 */
    public EnchantmentBuilderJS supportedItems(String tag) {
        this.supportedItemsTag = normalizeTag(tag);
        return this;
    }

    public EnchantmentBuilderJS weight(int weight) {
        this.weight = Math.max(1, weight);
        return this;
    }

    public EnchantmentBuilderJS maxLevel(int maxLevel) {
        this.maxLevel = Math.max(1, maxLevel);
        return this;
    }

    /** 最小消耗基础值（level=1 时的 {@code base}）。 */
    public EnchantmentBuilderJS minCost(int base) {
        return minCost(base, 0);
    }

    /** 最小消耗：{@code base + perLevel * (level - 1)}。 */
    public EnchantmentBuilderJS minCost(int base, int perLevel) {
        this.minCostBase = base;
        this.minCostPerLevel = perLevel;
        return this;
    }

    /** 最大消耗基础值。 */
    public EnchantmentBuilderJS maxCost(int base) {
        return maxCost(base, 0);
    }

    /** 最大消耗：{@code base + perLevel * (level - 1)}。 */
    public EnchantmentBuilderJS maxCost(int base, int perLevel) {
        this.maxCostBase = base;
        this.maxCostPerLevel = perLevel;
        return this;
    }

    public EnchantmentBuilderJS anvilCost(int anvilCost) {
        this.anvilCost = anvilCost;
        return this;
    }

    /** 生效槽位组（如 {@code 'mainhand'} / {@code 'armor'} / {@code 'any'}），默认 {@code mainhand}。 */
    public EnchantmentBuilderJS slots(String slotsStr) {
        this.slots = parseSlots(slotsStr);
        return this;
    }

    /**
     * 组装附魔实例。需要从 {@code RegisterEvent} 取 {@link Registry}（它同时是
     * {@code HolderLookup}）以解析物品标签为 {@link HolderSet}。26.x 下 ENCHANTMENT 注册
     * 晚于 ITEM 注册与物品标签绑定，{@code getOrThrow(TagKey)} 返回已绑定的
     * {@code HolderSet.Named}（与原版 {@link Enchantments} 同一调用方式）。
     */
    public Enchantment create(RegisterEvent event) {
        Registry<Item> itemRegistry = event.getRegistry(Registries.ITEM);
        HolderSet<Item> supported = resolveItems(itemRegistry);
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                supported,
                weight,
                maxLevel,
                Enchantment.dynamicCost(minCostBase, minCostPerLevel),
                Enchantment.dynamicCost(maxCostBase, maxCostPerLevel),
                anvilCost,
                slots);
        return Enchantment.enchantment(definition).build(location);
    }

    private HolderSet<Item> resolveItems(Registry<Item> itemRegistry) {
        if (supportedItemsTag == null || supportedItemsTag.isBlank()) {
            return HolderSet.empty();
        }
        Identifier tagId = Identifier.tryParse(supportedItemsTag);
        if (tagId == null) {
            return HolderSet.empty();
        }
        // 26.x: Registry<T> implements HolderGetter<T>；getOrThrow(TagKey) 返回 HolderSet.Named<T>
        return itemRegistry.getOrThrow(TagKey.create(Registries.ITEM, tagId));
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        return tag.startsWith("#") ? tag.substring(1) : tag;
    }

    private static EquipmentSlotGroup parseSlots(String value) {
        if (value == null) {
            return EquipmentSlotGroup.MAINHAND;
        }
        return switch (value.toLowerCase()) {
            case "any" -> EquipmentSlotGroup.ANY;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            case "body", "chest" -> EquipmentSlotGroup.CHEST;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "hands", "hand" -> EquipmentSlotGroup.HAND;
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }
}
