//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the {@code food}, {@code tool}, {@code attackDamage}, and {@code attackSpeed} properties on
 * {@link ItemModificationJS} to 26.x data components. {@link ItemModificationJS} holds the pending
 * values, while parsing, defaults, validation, and component construction live here.
 *
 * <h2>26.x component model (differences from 1.21.1)</h2>
 * <ul>
 *   <li>Food combines {@code FOOD} (nutrition values via {@link FoodProperties}) and
 *       {@code CONSUMABLE} (eating animation and duration via {@link Consumable}). Writing
 *       {@code FOOD} alone does not make an item edible because {@code Item#use} checks
 *       {@code CONSUMABLE}, and {@code eatSeconds} maps to {@code CONSUMABLE#consumeSeconds}
 *       rather than {@code FoodProperties}.</li>
 *   <li>Attack attributes map to the {@code minecraft:base_attack_damage} and
 *       {@code minecraft:base_attack_speed} modifiers in {@code ATTRIBUTE_MODIFIERS}
 *       ({@link AttributeModifier.Operation#ADD_VALUE} on mainhand). Matching KubeJS's
 *       {@code attackDamage}/{@code attackSpeed}, only entries for those attributes are replaced
 *       while all other modifiers are preserved.</li>
 *   <li>Tool maps to {@code TOOL} ({@link Tool}). In 26.x, mining tiers live on block components
 *       rather than items; "effective on all blocks" is represented by a direct {@link HolderSet}
 *       listing the block registry (vanilla has no all-blocks tag, and default components serialize
 *       only patches rather than writing the full set to NBT).</li>
 * </ul>
 *
 * <p>Script object literals arrive at {@code setter(Object)} as {@code Map<String, Object>} via
 * GraalJS conversion. Parsers accept only map objects (rejecting bare numbers and other primitives)
 * and fail on unknown keys with the list of valid keys, matching {@code rarity} error messages.
 */
public final class ItemModificationComponents {

    private static final Set<String> FOOD_KEYS = Set.of("nutrition", "saturation", "canAlwaysEat", "eatSeconds");
    private static final Set<String> TOOL_KEYS = Set.of("miningSpeed", "damagePerBlock", "canDestroyBlocksInCreative");

    private ItemModificationComponents() {}

    /**
     * Parsed food options. {@code saturation} is the 26.x builder's saturation modifier
     * (total saturation = {@code nutrition * saturation * 2}), matching KubeJS's {@code saturation}
     * field. A {@code null} {@code eatSeconds} leaves the eating duration unchanged.
     */
    public record FoodSpec(int nutrition, float saturation, boolean canAlwaysEat, @Nullable Float eatSeconds) {

        FoodProperties toProperties() {
            FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(this.nutrition)
                .saturationModifier(this.saturation);
            if (this.canAlwaysEat) {
                builder.alwaysEdible();
            }
            return builder.build();
        }

        /** Converts this spec into a normalized plain-Java map for {@code ModificationCandidatePlan}. */
        public Map<String, Object> toNormalized() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("nutrition", nutrition);
            map.put("saturation", saturation);
            map.put("canAlwaysEat", canAlwaysEat);
            if (eatSeconds != null) {
                map.put("eatSeconds", eatSeconds);
            }
            return map;
        }

        /** Reconstructs a {@code FoodSpec} from a normalized declaration map already validated during collection. */
        public static FoodSpec fromNormalized(Map<String, Object> map) {
            int nutrition = ((Number) map.getOrDefault("nutrition", 1)).intValue();
            float saturation = ((Number) map.getOrDefault("saturation", 0.1F)).floatValue();
            boolean canAlwaysEat = Boolean.TRUE.equals(map.getOrDefault("canAlwaysEat", false));
            Number eatSeconds = (Number) map.get("eatSeconds");
            return new FoodSpec(nutrition, saturation, canAlwaysEat,
                    eatSeconds == null ? null : eatSeconds.floatValue());
        }
    }

    /** Parsed tool options: mining speed across all blocks and durability damage per block. */
    public record ToolSpec(float miningSpeed, int damagePerBlock, boolean canDestroyBlocksInCreative) {

        Tool toTool() {
            HolderSet<Block> everyBlock = HolderSet.direct(BuiltInRegistries.BLOCK.listElements().toList());
            return new Tool(
                List.of(Tool.Rule.minesAndDrops(everyBlock, this.miningSpeed)),
                1.0F,
                this.damagePerBlock,
                this.canDestroyBlocksInCreative);
        }

        /** Converts this spec into a normalized plain-Java map for {@code ModificationCandidatePlan}. */
        public Map<String, Object> toNormalized() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("miningSpeed", miningSpeed);
            map.put("damagePerBlock", damagePerBlock);
            map.put("canDestroyBlocksInCreative", canDestroyBlocksInCreative);
            return map;
        }

        /** Reconstructs a {@code ToolSpec} from a normalized declaration map. */
        public static ToolSpec fromNormalized(Map<String, Object> map) {
            float miningSpeed = ((Number) map.get("miningSpeed")).floatValue();
            int damagePerBlock = ((Number) map.getOrDefault("damagePerBlock", 1)).intValue();
            boolean creative = Boolean.TRUE.equals(map.getOrDefault("canDestroyBlocksInCreative", true));
            return new ToolSpec(miningSpeed, damagePerBlock, creative);
        }
    }

    // ---------------------------------------------------------------- food

    static FoodSpec parseFood(Object value) {
        Map<?, ?> map = requireMap(value, "food", "{ nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }");
        rejectUnknownKeys(map, FOOD_KEYS, "food");
        int nutrition = intOption(map, "nutrition", 1);
        float saturation = floatOption(map, "saturation", 0.1F);
        boolean canAlwaysEat = booleanOption(map, "canAlwaysEat", false);
        Float eatSeconds = floatOption(map, "eatSeconds");
        if (nutrition < 0) {
            throw new IllegalArgumentException("Invalid food nutrition " + nutrition + ": must be >= 0");
        }
        if (saturation < 0.0F) {
            throw new IllegalArgumentException("Invalid food saturation " + saturation + ": must be >= 0");
        }
        if (eatSeconds != null && eatSeconds < 0.0F) {
            throw new IllegalArgumentException("Invalid food eatSeconds " + eatSeconds + ": must be >= 0");
        }
        return new FoodSpec(nutrition, saturation, canAlwaysEat, eatSeconds);
    }

    /**
     * Writes {@code FOOD} and ensures the item is edible: replaces {@code CONSUMABLE} (using
     * default animation and sound) when {@code eatSeconds} is set; otherwise keeps any existing
     * {@code CONSUMABLE} (preserving custom potion effects, etc.) or adds a default 1.6-second one.
     */
    static void applyFood(DataComponentMap.Builder builder, DataComponentMap base, FoodSpec food) {
        builder.set(DataComponents.FOOD, food.toProperties());
        if (food.eatSeconds() != null) {
            builder.set(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(food.eatSeconds()).build());
        } else if (base.get(DataComponents.CONSUMABLE) == null) {
            builder.set(DataComponents.CONSUMABLE, Consumable.builder().build());
        }
    }

    /**
     * Removes {@code FOOD}, and also removes {@code CONSUMABLE} if {@code base} originally had
     * {@code FOOD} (otherwise right-clicking would still play the eating animation and consume the
     * item). Items that only had {@code CONSUMABLE} without {@code FOOD} (such as potions) keep it.
     */
    static void removeFood(DataComponentMap.Builder builder, DataComponentMap base) {
        builder.set(DataComponents.FOOD, null);
        if (base.get(DataComponents.FOOD) != null) {
            builder.set(DataComponents.CONSUMABLE, null);
        }
    }

    // ---------------------------------------------------------------- tool

    static ToolSpec parseTool(Object value) {
        Map<?, ?> map = requireMap(value, "tool", "{ miningSpeed: 6, damagePerBlock: 1, canDestroyBlocksInCreative: true }");
        rejectUnknownKeys(map, TOOL_KEYS, "tool");
        Float miningSpeed = floatOption(map, "miningSpeed");
        if (miningSpeed == null) {
            throw new IllegalArgumentException("Invalid tool: 'miningSpeed' is required (e.g. { miningSpeed: 6 })");
        }
        if (miningSpeed <= 0.0F) {
            throw new IllegalArgumentException("Invalid tool miningSpeed " + miningSpeed + ": must be > 0");
        }
        int damagePerBlock = intOption(map, "damagePerBlock", 1);
        if (damagePerBlock < 0) {
            throw new IllegalArgumentException("Invalid tool damagePerBlock " + damagePerBlock + ": must be >= 0");
        }
        boolean canDestroyBlocksInCreative = booleanOption(map, "canDestroyBlocksInCreative", true);
        return new ToolSpec(miningSpeed, damagePerBlock, canDestroyBlocksInCreative);
    }

    static void applyTool(DataComponentMap.Builder builder, ToolSpec tool) {
        builder.set(DataComponents.TOOL, tool.toTool());
    }

    // ---------------------------------------------------------- attributes

    /**
     * Replaces {@code base_attack_damage} and/or {@code base_attack_speed} modifiers while keeping
     * all other attribute entries (including the other attack attribute and armor) and reusing the
     * first replaced entry's tooltip display, matching KubeJS 8's {@code attackDamage}/{@code attackSpeed}.
     */
    static void applyAttributes(DataComponentMap.Builder builder, DataComponentMap base,
                                @Nullable Double attackDamage, @Nullable Double attackSpeed) {
        ItemAttributeModifiers current = base.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        builder.set(DataComponents.ATTRIBUTE_MODIFIERS, patchAttributeModifiers(current, attackDamage, attackSpeed));
    }

    private static ItemAttributeModifiers patchAttributeModifiers(ItemAttributeModifiers current,
                                                                  @Nullable Double attackDamage, @Nullable Double attackSpeed) {
        List<ItemAttributeModifiers.Entry> kept = new ArrayList<>(current.modifiers().size());
        ItemAttributeModifiers.Display damageDisplay = null;
        ItemAttributeModifiers.Display speedDisplay = null;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            if (attackDamage != null && entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                if (damageDisplay == null) damageDisplay = entry.display();
                continue;
            }
            if (attackSpeed != null && entry.attribute().equals(Attributes.ATTACK_SPEED)) {
                if (speedDisplay == null) speedDisplay = entry.display();
                continue;
            }
            kept.add(entry);
        }
        if (attackDamage != null) {
            kept.add(new ItemAttributeModifiers.Entry(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
                damageDisplay == null ? ItemAttributeModifiers.Display.attributeModifiers() : damageDisplay));
        }
        if (attackSpeed != null) {
            kept.add(new ItemAttributeModifiers.Entry(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
                speedDisplay == null ? ItemAttributeModifiers.Display.attributeModifiers() : speedDisplay));
        }
        return new ItemAttributeModifiers(List.copyOf(kept));
    }

    // -------------------------------------------------------------- parsing

    private static Map<?, ?> requireMap(Object value, String property, String example) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Invalid " + property + " '" + value + "': expected an object like " + example);
    }

    private static void rejectUnknownKeys(Map<?, ?> map, Set<String> known, String property) {
        for (Object key : map.keySet()) {
            if (key instanceof String name && !known.contains(name)) {
                throw new IllegalArgumentException("Unknown " + property + " option '" + name + "': expected one of " + String.join(", ", known));
            }
        }
    }

    private static int intOption(Map<?, ?> map, String key, int defaultValue) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a number, got '" + raw + "'");
    }

    private static float floatOption(Map<?, ?> map, String key, float defaultValue) {
        Float value = floatOption(map, key);
        return value == null ? defaultValue : value;
    }

    private static @Nullable Float floatOption(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a number, got '" + raw + "'");
    }

    private static boolean booleanOption(Map<?, ?> map, String key, boolean defaultValue) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a boolean, got '" + raw + "'");
    }
}
//?}
