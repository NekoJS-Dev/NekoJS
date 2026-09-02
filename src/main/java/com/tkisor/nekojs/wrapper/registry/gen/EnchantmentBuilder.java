// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔 builder：1.21+ 的 {@link Enchantment} 是 record，由
 * {@link Enchantment.EnchantmentDefinition} 描述属性。脚本仅提供基础数值与一个
 * 物品标签 id（决定可附魔物品集合），build 期从 {@link BuiltInRegistries#ITEM}
 * 解析（与 RegisterEvent.getRegistry 同一注册表实例）。
 * 默认不挂 effects（注册成功、可被附魔/书本/互斥，但无实际效果）。
 * <pre>
 * event.enchantment('mymod:venom', b =&gt; { b.supportedItems = 'minecraft:enchantable/weapon'; b.maxLevel = 3 })
 * </pre>
 */
public class EnchantmentBuilder extends RegistryObjectBuilder<Enchantment>
        implements TaggableBuilder<EnchantmentBuilder> {

    /** 支持附魔的物品标签 id（如 'minecraft:enchantable/weapon'，'#' 前缀 tolerated）。默认空集合。 */
    public String supportedItems = null;
    public int weight = 1;
    public int maxLevel = 1;
    /** 最小消耗：{@code minCostBase + minCostPerLevel * (level - 1)}。 */
    public int minCostBase = 1;
    public int minCostPerLevel = 0;
    /** 最大消耗：{@code maxCostBase + maxCostPerLevel * (level - 1)}。 */
    public int maxCostBase = 1;
    public int maxCostPerLevel = 0;
    public int anvilCost = 0;
    /** 生效槽位组：any/armor/chest/feet/head/legs/hand/mainhand/offhand（默认 mainhand）。 */
    public String slots = "mainhand";

    public EnchantmentBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}：附魔 tag（如 {@code minecraft:treasure}）归属 ENCHANTMENT 注册表。 */
    @Override
    public ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.ENCHANTMENT;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    @Override
    public Enchantment build() {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                resolveItems(),
                Math.max(1, weight),
                Math.max(1, maxLevel),
                Enchantment.dynamicCost(minCostBase, minCostPerLevel),
                Enchantment.dynamicCost(maxCostBase, maxCostPerLevel),
                anvilCost,
                resolveSlots(slots));
        return Enchantment.enchantment(definition).build(id);
    }

    private HolderSet<Item> resolveItems() {
        if (supportedItems == null || supportedItems.isBlank()) {
            return HolderSet.empty();
        }
        String normalized = supportedItems.startsWith("#") ? supportedItems.substring(1) : supportedItems;
        Identifier tagId = Identifier.tryParse(normalized);
        if (tagId == null) {
            return HolderSet.empty();
        }
        // 26.x: Registry<T> implements HolderGetter；ENCHANTMENT 注册晚于 ITEM 注册与物品标签绑定
        return BuiltInRegistries.ITEM.getOrThrow(TagKey.create(Registries.ITEM, tagId));
    }

    private static EquipmentSlotGroup resolveSlots(String value) {
        return switch (value == null ? "mainhand" : value.toLowerCase()) {
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
