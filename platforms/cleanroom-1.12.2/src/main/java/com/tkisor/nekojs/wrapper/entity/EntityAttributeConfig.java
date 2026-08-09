package com.tkisor.nekojs.wrapper.entity;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1.12.2 entity attribute configuration container + builder.
 *
 * <p>Holds a set of {@code (IAttribute -> baseValue)} overrides and applies them onto an
 * entity's {@link AbstractAttributeMap} via {@link #applyTo(AbstractAttributeMap)}.
 * Configurations are keyed by the entity's registry {@link ResourceLocation} so that
 * {@code NekoScriptMob} (which all custom mobs share) can resolve the right config in
 * {@code applyEntityAttributes()}.
 */
public class EntityAttributeConfig {

    /** Insertion-ordered map so applied values are deterministic for debugging. */
    private final Map<IAttribute, Double> values = new LinkedHashMap<>();

    /** Lookup table indexed by entity registry id. Populated at entity-build time, read at runtime. */
    private static final Map<ResourceLocation, EntityAttributeConfig> BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

    public EntityAttributeConfig maxHealth(double value) {
        values.put(SharedMonsterAttributes.MAX_HEALTH, value);
        return this;
    }

    public EntityAttributeConfig movementSpeed(double value) {
        values.put(SharedMonsterAttributes.MOVEMENT_SPEED, value);
        return this;
    }

    public EntityAttributeConfig flyingSpeed(double value) {
        values.put(SharedMonsterAttributes.FLYING_SPEED, value);
        return this;
    }

    public EntityAttributeConfig followRange(double value) {
        values.put(SharedMonsterAttributes.FOLLOW_RANGE, value);
        return this;
    }

    public EntityAttributeConfig attackDamage(double value) {
        values.put(SharedMonsterAttributes.ATTACK_DAMAGE, value);
        return this;
    }

    public EntityAttributeConfig armor(double value) {
        values.put(SharedMonsterAttributes.ARMOR, value);
        return this;
    }

    public EntityAttributeConfig armorToughness(double value) {
        values.put(SharedMonsterAttributes.ARMOR_TOUGHNESS, value);
        return this;
    }

    public EntityAttributeConfig knockbackResistance(double value) {
        values.put(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, value);
        return this;
    }

    /**
     * Apply all configured attributes onto the given attribute map.
     *
     * <p>{@link AbstractAttributeMap#registerAttribute(IAttribute)} is idempotent, so it is
     * safe to call even for attributes the entity already has (it will just return the
     * existing instance). We then set the base value via
     * {@code setBaseValue(double)}.
     */
    public void applyTo(AbstractAttributeMap map) {
        for (Map.Entry<IAttribute, Double> entry : values.entrySet()) {
            map.registerAttribute(entry.getKey()).setBaseValue(entry.getValue());
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Register a config under the given entity id (called from EntityTypeBuilderJS.build). */
    public static void register(ResourceLocation id, EntityAttributeConfig config) {
        if (id == null || config == null || config.isEmpty()) {
            return;
        }
        BY_ID.put(id, config);
    }

    /** Look up the config for the given entity id (called from NekoScriptMob.applyEntityAttributes). */
    public static EntityAttributeConfig get(ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }
}
