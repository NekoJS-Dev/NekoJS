// 26.x implementation. Version guards should not appear in this file again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; when changing behavior here, update it too.
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
 * Enchantment builder. In 1.21+ {@link Enchantment} is a record, and
 * {@link Enchantment.EnchantmentDefinition} describes its properties. Scripts supply only the base
 * numbers and one item tag id that sets the enchantable items; build resolves that tag from
 * {@link BuiltInRegistries#ITEM}, the same registry instance as RegisterEvent.getRegistry.
 * No effects are attached by default, so the enchantment registers, can be applied to items and
 * books, and takes part in exclusivity, but has no actual effect.
 * <pre>
 * event.enchantment('mymod:venom', b =&gt; { b.supportedItems = 'minecraft:enchantable/weapon'; b.maxLevel = 3 })
 * </pre>
 */
public class EnchantmentBuilder extends RegistryObjectBuilder<Enchantment>
        implements TaggableBuilder<EnchantmentBuilder> {

    /** Item tag id of the enchantable items (for example 'minecraft:enchantable/weapon'; a leading '#'
     * is tolerated). Defaults to empty. */
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
    /** Slot groups where the enchantment applies: any/armor/chest/feet/head/legs/hand/mainhand/offhand
     * (default mainhand). */
    private String slots = "mainhand";

    public EnchantmentBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}: the enchantment tag (such as {@code minecraft:treasure}) belongs to the
     * ENCHANTMENT registry. */
    @Override
    public ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.ENCHANTMENT;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    // ---- Data properties: the explicit setter and the JavaBean property write through the same path ----

    /** Item tag id of the enchantable items. Normalized: blank means undeclared, which follows the same
     * branch at build time anyway. */
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

    /** Slot groups where the enchantment applies, normalized to lower case. */
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
        Identifier tagId = Identifier.tryParse(normalized);
        if (tagId == null) {
            return HolderSet.empty();
        }
        // 26.x: Registry<T> implements HolderGetter; ENCHANTMENT registers after ITEM registration and item tag
        // binding
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
