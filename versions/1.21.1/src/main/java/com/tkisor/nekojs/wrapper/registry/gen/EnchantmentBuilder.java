// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
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
    private String supportedItems = null;
    private int weight = 1;
    private int maxLevel = 1;
    /** 最小消耗：{@code minCostBase + minCostPerLevel * (level - 1)}。 */
    private int minCostBase = 1;
    private int minCostPerLevel = 0;
    /** 最大消耗：{@code maxCostBase + maxCostPerLevel * (level - 1)}。 */
    private int maxCostBase = 1;
    private int maxCostPerLevel = 0;
    private int anvilCost = 0;
    /** 生效槽位组：any/armor/chest/feet/head/legs/hand/mainhand/offhand（默认 mainhand）。 */
    private String slots = "mainhand";

    public EnchantmentBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点（ticket 15） ----

    /** 支持附魔的物品标签 id（已归一化：空白视为未声明——build 期本来就走同一分支）。 */
    public String getSupportedItems() {
        return supportedItems;
    }

    public void setSupportedItems(String supportedItems) {
        this.supportedItems = supportedItems == null || supportedItems.isBlank() ? null : supportedItems;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getMinCostBase() {
        return minCostBase;
    }

    public void setMinCostBase(int minCostBase) {
        this.minCostBase = minCostBase;
    }

    public int getMinCostPerLevel() {
        return minCostPerLevel;
    }

    public void setMinCostPerLevel(int minCostPerLevel) {
        this.minCostPerLevel = minCostPerLevel;
    }

    public int getMaxCostBase() {
        return maxCostBase;
    }

    public void setMaxCostBase(int maxCostBase) {
        this.maxCostBase = maxCostBase;
    }

    public int getMaxCostPerLevel() {
        return maxCostPerLevel;
    }

    public void setMaxCostPerLevel(int maxCostPerLevel) {
        this.maxCostPerLevel = maxCostPerLevel;
    }

    public int getAnvilCost() {
        return anvilCost;
    }

    public void setAnvilCost(int anvilCost) {
        this.anvilCost = anvilCost;
    }

    /** 生效槽位组（已归一化小写）。 */
    public String getSlots() {
        return slots;
    }

    public void setSlots(String slots) {
        this.slots = slots == null ? "mainhand" : slots.toLowerCase();
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
