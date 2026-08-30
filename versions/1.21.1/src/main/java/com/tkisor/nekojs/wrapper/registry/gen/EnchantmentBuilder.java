// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
// TODO(loader-port): deferred to the LoaderBridge fabric port
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
public class EnchantmentBuilder extends RegistryObjectBuilder<Enchantment> {

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

    public EnchantmentBuilder(ResourceLocation id) {
        super(id);
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
        ResourceLocation tagId = ResourceLocation.tryParse(normalized);
        if (tagId == null) {
            return HolderSet.empty();
        }
        // 26.x: Registry<T> implements HolderGetter；ENCHANTMENT 注册晚于 ITEM 注册与物品标签绑定
        return BuiltInRegistries.ITEM.getOrCreateTag(TagKey.create(Registries.ITEM, tagId));
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
