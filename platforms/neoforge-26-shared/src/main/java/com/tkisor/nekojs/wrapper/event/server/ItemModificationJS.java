package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DamageResistant;

/**
 * Mutable property view handed to {@code ItemEvents.modification} callbacks.
 *
 * <h2>JS API</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;
 *     item.rarity = 'epic';
 *     item.fireResistant = true;
 *   });
 * });
 * </pre>
 *
 * <p>Unset properties keep the item's current (pre-modification) values. Writing
 * a property applies it to the item's default {@link DataComponentMap} when the
 * enclosing event finishes, so all stacks of that item pick up the change.
 */
public class ItemModificationJS {

    /** 26.x 组件上限：{@link net.minecraft.world.item.Item#ABSOLUTE_MAX_STACK_SIZE}。 */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

    private Integer maxStackSize;
    private Integer maxDamage;
    private Rarity rarity;
    private Boolean fireResistant;

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
     * Writes the requested properties into {@code builder} (seeded with the item's
     * pristine components), validating the durability/stacking invariant first.
     */
    void applyTo(DataComponentMap.Builder builder, DataComponentMap base, MinecraftServer server) {
        validate(base);
        if (maxStackSize != null) {
            builder.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        }
        if (maxDamage != null) {
            builder.set(DataComponents.MAX_DAMAGE, maxDamage);
        }
        if (rarity != null) {
            builder.set(DataComponents.RARITY, rarity);
        }
        if (fireResistant != null) {
            builder.set(DataComponents.DAMAGE_RESISTANT, fireResistant ? createFireResistance(server) : null);
        }
    }

    /**
     * 校验组件不变量：stack size 1..99、maxDamage {@code >= 0}，
     * 且 maxStackSize{@code >1} 与 maxDamage{@code >0} 不可并存（可堆叠物品不可损耗）。
     * 未显式设置的字段按物品原始组件取默认值参与判断。
     */
    private void validate(DataComponentMap base) {
        if (maxStackSize != null && (maxStackSize < 1 || maxStackSize > MAX_STACK_SIZE_LIMIT)) {
            throw new IllegalArgumentException("Invalid maxStackSize " + maxStackSize + ": must be between 1 and " + MAX_STACK_SIZE_LIMIT);
        }
        if (maxDamage != null && maxDamage < 0) {
            throw new IllegalArgumentException("Invalid maxDamage " + maxDamage + ": must be >= 0");
        }
        int effectiveStack = maxStackSize != null ? maxStackSize : base.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        int effectiveDamage = maxDamage != null ? maxDamage : base.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (effectiveStack > 1 && effectiveDamage > 0) {
            throw new IllegalArgumentException(
                "Cannot combine maxStackSize=" + effectiveStack + " with maxDamage=" + effectiveDamage
                + ": stackable items cannot be damageable (set maxStackSize = 1)");
        }
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

    /**
     * 26.x 走 DAMAGE_RESISTANT 组件（指向 IS_FIRE damage type tag），需要 registry access。
     * 与 vanilla {@code Item.Properties#fireResistant()} 同构：从 damage type registry
     * 解析 IS_FIRE tag 的实际条目（比空命名 HolderSet 更接近原版行为）。
     */
    private static DamageResistant createFireResistance(MinecraftServer server) {
        return new DamageResistant(
            server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE));
    }
}
