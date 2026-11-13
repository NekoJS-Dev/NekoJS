// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// The content is that file evaluated at this node; keep this file in sync when 26.x behavior changes.
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
 * Enchantment builder. In 1.21+ {@link Enchantment} is a record described by
 * {@link Enchantment.EnchantmentDefinition}. The script only supplies the base numbers and one
 * item tag id, which decides the set of enchantable items; build resolves that tag from
 * {@link BuiltInRegistries#ITEM}, the same registry instance as RegisterEvent.getRegistry.
 * No effects are attached by default: the enchantment registers, can be enchanted, put on books
 * and conflicts with others, but has no actual effect.
 * <pre>
 * event.enchantment('mymod:venom', b =&gt; { b.supportedItems = 'minecraft:enchantable/weapon'; b.maxLevel = 3 })
 * </pre>
 */
public class EnchantmentBuilder extends RegistryObjectBuilder<Enchantment> {

    /**
     * Item tag id that can be enchanted (such as 'minecraft:enchantable/weapon';
     * a leading '#' is tolerated). Empty set by default.
     */
    private String supportedItems = null;
    private int weight = 1;
    private int maxLevel = 1;
    /** Minimum cost: {@code minCostBase + minCostPerLevel * (level - 1)}. */
    private int minCostBase = 1;
    private int minCostPerLevel = 0;
    /** Maximum cost: {@code maxCostBase + maxCostPerLevel * (level - 1)}. */
    private int maxCostBase = 1;
    private int maxCostPerLevel = 0;
    private int anvilCost = 0;
    /** Slot groups the enchantment applies to: any/armor/chest/feet/head/legs/hand/mainhand/offhand (default mainhand). */
    private String slots = "mainhand";

    public EnchantmentBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- Data properties: the explicit setter and the JavaBean property share one write point ----

    /**
     * Item tag id that can be enchanted, normalized: blank counts as not declared,
     * which build takes the same branch for anyway.
     */
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

    /** Slot groups, normalized to lower case. */
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
        // 26.x: Registry<T> implements HolderGetter; ENCHANTMENT registers after ITEM and after item tags are bound
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
