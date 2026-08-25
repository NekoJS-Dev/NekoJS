package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
 * <p>Unset properties keep the item's current (pre-modification) values. Writing
 * a property applies it to the item's default {@link DataComponentMap} when the
 * enclosing event finishes, so all stacks of that item pick up the change.
 * Properties that accept {@code null} (food, tool) remove the corresponding
 * component instead. The food/tool/attribute mappings live in
 * {@link ItemModificationComponents}.
 */
public class ItemModificationJS {

    /** 26.x 组件上限：{@link net.minecraft.world.item.Item#ABSOLUTE_MAX_STACK_SIZE}。 */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

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
     * 待写入的食物配置；{@code null} 表示未设置（保持原值）或已被 {@code item.food = null}
     * 请求移除。
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
     * 待写入的工具配置；{@code null} 表示未设置或已被 {@code item.tool = null} 请求移除。
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
     * 待写入的基础攻击伤害加成；{@code null} = 保持原值。数值为修饰量（玩家基础攻击
     * 伤害 2.0 不包含在内），语义同 KubeJS 8 的 attackDamage。
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
     * 待写入的基础攻击速度加成；{@code null} = 保持原值。数值为修饰量（玩家基础攻击
     * 速度 4.0 不包含在内），语义同 KubeJS 8 的 attackSpeed。
     */
    @Doc("Replaces the item's base attack speed modifier (minecraft:base_attack_speed, ADD_VALUE on mainhand), keeping every other attribute entry - including attack damage.")
    @Doc("The value is a bonus on top of the player's base attack speed (4.0 on 26.x): a sword uses -2.4 for a total of 1.6.")
    @Return("Pending attack speed override, or null when not set.")
    public Double getAttackSpeed() {
        return attackSpeed;
    }

    @Doc("Replaces the item's base attack speed modifier (minecraft:base_attack_speed, ADD_VALUE on mainhand), keeping every other attribute entry - including attack damage.")
    @Doc("The value is a bonus on top of the player's base attack speed (4.0 on 26.x): a sword uses -2.4 for a total of 1.6.")
    @Param(name = "speed", value = "New base attack speed bonus, e.g. -2.4 for sword-like speed.")
    public void setAttackSpeed(double speed) {
        this.attackSpeed = speed;
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
        if (food != null) {
            ItemModificationComponents.applyFood(builder, base, food);
        } else if (removeFood) {
            ItemModificationComponents.removeFood(builder, base);
        }
        if (tool != null) {
            ItemModificationComponents.applyTool(builder, tool);
        } else if (removeTool) {
            builder.set(DataComponents.TOOL, null);
        }
        if (attackDamage != null || attackSpeed != null) {
            ItemModificationComponents.applyAttributes(builder, base, attackDamage, attackSpeed);
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
