package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Set;

/**
 * Builder for an Item dynamic definition; it is the typed callback builder behind the direct
 * entry point {@code event.item(id, cb)}. Its configuration shape matches the
 * {@code DynamicRegistryJS.ItemBuilder} one (stack size, rarity, fire resistant, mode), but values
 * are validated and normalized and then enter an inert plan: the candidate phase creates no MC
 * object. The Adapter resolves MC enums such as Rarity at the point where applying them is legal,
 * and this class only holds normalized string values.
 */
public final class DynamicItemBuilder extends DynamicDefinitionBuilder {

    /** Valid range for vanilla Item.Properties#stacksTo. */
    public static final int MAX_STACK_SIZE_MIN = 1;
    public static final int MAX_STACK_SIZE_MAX = 99;

    /** Closed set of strings matching vanilla Rarity; compared after normalization to lower case. */
    public static final Set<String> RARITIES = Set.of("common", "uncommon", "rare", "epic");

    private int maxStackSize = 64;
    private String rarity = "common";
    private boolean fireResistant;

    /** Max stack size (1..99, default 64); an out-of-range value throws at the write site in both write paths. */
    public DynamicItemBuilder setMaxStackSize(int size) {
        if (size < MAX_STACK_SIZE_MIN || size > MAX_STACK_SIZE_MAX) {
            throw new IllegalArgumentException(
                    "Invalid maxStackSize " + size + ": must be between " + MAX_STACK_SIZE_MIN + " and "
                            + MAX_STACK_SIZE_MAX + " (got " + size + ")");
        }
        this.maxStackSize = size;
        return this;
    }

    /** Rarity: common (default), uncommon, rare or epic. Case-insensitive; normalized to lower case. */
    public DynamicItemBuilder setRarity(String rarity) {
        String normalized = DynamicDefinitionType.normalizeToken(rarity, "rarity");
        if (!RARITIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown rarity '" + rarity.trim() + "': expected one of common, uncommon, rare, epic");
        }
        this.rarity = normalized;
        return this;
    }

    /** Whether the item is fire resistant (default false). */
    public DynamicItemBuilder setFireResistant(boolean value) {
        this.fireResistant = value;
        return this;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public String getRarity() {
        return rarity;
    }

    public boolean isFireResistant() {
        return fireResistant;
    }

    @Override
    public String toString() {
        return "DynamicItemBuilder[maxStackSize=" + maxStackSize + ", rarity=" + rarity
                + ", fireResistant=" + fireResistant + ", mode=" + getMode() + "]";
    }
}
