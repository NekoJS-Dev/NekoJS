//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable property view handed to {@code ItemEvents.modification} callbacks.
 * During collection this view only buffers normalized property overrides;
 * {@code ModificationDomainOwner} applies them to live {@code Item} default components at commit time.
 *
 * <h2>JS API (property writes and explicit setters share the same setter via {@link ModificationViewSurface})</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;          // equivalent to item.setMaxStackSize(16)
 *     item.rarity = 'epic';
 *     item.fireResistant = true;
 *   });
 *   event.modify('minecraft:stick', item {@code ->} {
 *     item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 };
 *   });
 *   event.modify('minecraft:blaze_rod', item {@code ->} {
 *     item.tool = { miningSpeed: 6 };
 *     item.attackDamage = 6;
 *     item.attackSpeed = -2.4;
 *   });
 * });
 * </pre>
 *
 * <p>Unset properties keep the item's baseline values. Writing a property records it
 * in the declaration ({@link #normalizedProperties()}); properties that accept
 * {@code null} (food, tool) request removal of the corresponding component.
 * The food/tool/attribute mappings live in {@link ItemModificationComponents}.
 */
public class ItemModificationJS implements graal.graalvm.polyglot.proxy.ProxyObject {

    private ModificationViewSurface surface;

    /** Lazily creates the proxy member dispatcher after subclass construction completes. */
    private ModificationViewSurface surface() {
        if (surface == null) surface = ModificationViewSurface.of(this);
        return surface;
    }

    // ---- ProxyObject: property writes and explicit setters share the same Java setter ----

    @Override
    public Object getMember(String name) {
        return surface().getMember(name);
    }

    @Override
    public boolean hasMember(String name) {
        return surface().hasMember(name);
    }

    @Override
    public Object getMemberKeys() {
        return surface().getMemberKeys();
    }

    @Override
    public void putMember(String name, graal.graalvm.polyglot.Value value) {
        surface().putMember(name, value);
    }

    @Override
    public boolean removeMember(String name) {
        return surface().removeMember(name);
    }

    private Integer maxStackSize;
    private Integer maxDamage;
    private Rarity rarity;
    private Boolean fireResistant;
    private ItemModificationComponents.FoodSpec food;
    private boolean removeFood;
    private ItemModificationComponents.ToolSpec tool;
    private boolean removeTool;
    private Double attackDamage;
    private Double attackSpeed;

    /** Maximum stack size (1..99). {@code null} = keep current value. */
    public Integer getMaxStackSize() {
        return maxStackSize;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    /** Maximum damage / durability ({@code >= 0}). {@code null} = keep current value. */
    public Integer getMaxDamage() {
        return maxDamage;
    }

    public void setMaxDamage(int maxDamage) {
        this.maxDamage = maxDamage;
    }

    /**
     * Current rarity as its serialized name ({@code 'common'/'uncommon'/'rare'/'epic'}),
     * or {@code null} when not set.
     */
    public String getRarity() {
        return rarity == null ? null : rarity.getSerializedName();
    }

    /**
     * Sets the rarity, either by serialized name ({@code 'epic'}) or by a
     * {@link Rarity} instance.
     */
    public void setRarity(Object value) {
        if (value == null) {
            this.rarity = null;
        } else if (value instanceof Rarity rarityValue) {
            this.rarity = rarityValue;
        } else if (value instanceof String name) {
            this.rarity = parseRarity(name);
        } else {
            throw new IllegalArgumentException("Invalid rarity '" + value + "': expected a string like 'epic' or a Rarity");
        }
    }

    /** Whether the item should resist fire/lava damage. {@code null} = keep current value. */
    public Boolean getFireResistant() {
        return fireResistant;
    }

    public void setFireResistant(boolean fireResistant) {
        this.fireResistant = fireResistant;
    }

    /**
     * Pending food override, or {@code null} if unset (keep current value) or cleared via
     * {@code item.food = null}.
     */
    @Doc("The pending food override as { nutrition, saturation, canAlwaysEat, eatSeconds }, or null when unset (keep current food) or when removal was requested with item.food = null.")
    @Return("Pending food spec, or null.")
    public ItemModificationComponents.FoodSpec getFood() {
        return food;
    }

    @Doc("Replaces the item's food component (minecraft:food).")
    @Doc("Options: nutrition (default 1), saturation (default 0.1, a modifier: absolute saturation = nutrition * saturation * 2), canAlwaysEat (default false), eatSeconds (optional eating duration in seconds).")
    @Doc("The item also gets a consumable component so it can actually be eaten: a default one (1.6s) when it had none, the existing one when eatSeconds is omitted, or a fresh one with the given duration when eatSeconds is set.")
    @Doc("Pass null to remove food entirely: a previously edible item loses its eating animation component too; drink-only items (potions) keep theirs.")
    @Param(name = "value", value = "Object like { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }, or null to remove food.")
    public void setFood(Object value) {
        if (value == null) {
            this.food = null;
            this.removeFood = true;
        } else {
            this.food = ItemModificationComponents.parseFood(value);
            this.removeFood = false;
        }
    }

    /**
     * Pending tool override, or {@code null} if unset or cleared via {@code item.tool = null}.
     */
    @Doc("The pending tool override as { miningSpeed, damagePerBlock, canDestroyBlocksInCreative }, or null when unset or when removal was requested.")
    @Return("Pending tool spec, or null.")
    public ItemModificationComponents.ToolSpec getTool() {
        return tool;
    }

    @Doc("Makes the item an effective tool for every block by writing minecraft:tool with a single catch-all rule: given mining speed everywhere plus correct-for-drops (obsidian etc. will drop).")
    @Doc("Options: miningSpeed (required, > 0), damagePerBlock (default 1 durability per block), canDestroyBlocksInCreative (default true).")
    @Doc("26.x has no item-side mining level (tool tiers live on block components), so there is no level to set here. Pass null to remove the tool component.")
    @Param(name = "value", value = "Object like { miningSpeed: 6 }, or null to remove the tool component.")
    public void setTool(Object value) {
        if (value == null) {
            this.tool = null;
            this.removeTool = true;
        } else {
            this.tool = ItemModificationComponents.parseTool(value);
            this.removeTool = false;
        }
    }

    /**
     * Pending base attack damage bonus, or {@code null} to keep the current value. This is the
     * modifier amount excluding the player's 2.0 base attack damage, matching KubeJS 8's
     * {@code attackDamage}.
     */
    @Doc("Replaces the item's base attack damage modifier (minecraft:base_attack_damage, ADD_VALUE on mainhand), keeping every other attribute entry - including attack speed.")
    @Doc("The value is a bonus on top of the player's base attack damage (2.0 on 26.x), so total damage = 2.0 + value.")
    @Return("Pending attack damage override, or null when not set.")
    public Double getAttackDamage() {
        return attackDamage;
    }

    @Doc("Replaces the item's base attack damage modifier (minecraft:base_attack_damage, ADD_VALUE on mainhand), keeping every other attribute entry - including attack speed.")
    @Doc("The value is a bonus on top of the player's base attack damage (2.0 on 26.x), so total damage = 2.0 + value. Swords use around 3..8.")
    @Param(name = "damage", value = "New base attack damage bonus, e.g. 6 for a strong weapon.")
    public void setAttackDamage(double damage) {
        this.attackDamage = damage;
    }

    /**
     * Pending base attack speed bonus, or {@code null} to keep the current value. This is the
     * modifier amount excluding the player's 4.0 base attack speed, matching KubeJS 8's
     * {@code attackSpeed}.
     */
    @Doc("Replaces the item's base attack speed modifier (minecraft:base_attack_speed, ADD_VALUE on mainhand), keeping every other attribute entry - including attack damage.")
    @Doc("The value is a bonus on top of the player's base attack speed (4.0 on 26.x): a sword uses -2.4 for a total of 1.6.")
    @Return("Pending attack speed override, or null when not set.")
    public Double getAttackSpeed() {
        return attackSpeed;
    }

    @Doc("Replaces the item's base attack speed modifier (minecraft:base_attack_speed, ADD_VALUE on mainhand), keeping every other attribute entry - including attack damage.")
    @Doc("The value is a bonus on top of the player's base attack speed (4.0 on 26.x): a sword uses -2.4 for sword-like speed.")
    @Param(name = "speed", value = "New base attack speed bonus, e.g. -2.4 for sword-like speed.")
    public void setAttackSpeed(double speed) {
        this.attackSpeed = speed;
    }

    /**
     * Returns the normalized map of properties explicitly set on this view. {@code food} and
     * {@code tool} map to normalized maps, or {@code null} when removal was requested.
     * {@code ModificationDomainOwner} checks stack/damage constraints and target existence during
     * {@code STATE_PLAN} preflight.
     */
    Map<String, Object> normalizedProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (maxStackSize != null) properties.put("maxStackSize", maxStackSize);
        if (maxDamage != null) properties.put("maxDamage", maxDamage);
        if (rarity != null) properties.put("rarity", rarity.getSerializedName());
        if (fireResistant != null) properties.put("fireResistant", fireResistant);
        if (food != null) {
            properties.put("food", food.toNormalized());
        } else if (removeFood) {
            properties.put("food", null);
        }
        if (tool != null) {
            properties.put("tool", tool.toNormalized());
        } else if (removeTool) {
            properties.put("tool", null);
        }
        if (attackDamage != null) properties.put("attackDamage", attackDamage);
        if (attackSpeed != null) properties.put("attackSpeed", attackSpeed);
        return properties;
    }

    private static Rarity parseRarity(String name) {
        return switch (name.toLowerCase()) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalArgumentException("Unknown rarity '" + name + "': expected one of common, uncommon, rare, epic");
        };
    }
}
//?}
