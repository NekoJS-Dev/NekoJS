package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Attribute overrides for a custom mob; configured via EntityTypeBuilderJS.attributes(callback).")
public class EntityAttributeConfig {

    /** Insertion-ordered map so applied values are deterministic for debugging. */
    private final Map<IAttribute, Double> values = new LinkedHashMap<>();

    /** Lookup table indexed by entity registry id. Populated at entity-build time, read at runtime. */
    private static final Map<ResourceLocation, EntityAttributeConfig> BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

    /** Sets max health. */
    @Doc("Sets the maximum health; vanilla default is 20.")
    @Param(name = "value", value = "max health in half-hearts")
    @Return("this config, for chaining")
    public EntityAttributeConfig maxHealth(double value) {
        values.put(SharedMonsterAttributes.MAX_HEALTH, value);
        return this;
    }

    /** Sets ground movement speed. */
    @Doc("Sets the ground movement speed; vanilla typical value is 0.3.")
    @Param(name = "value", value = "movement speed factor")
    @Return("this config, for chaining")
    public EntityAttributeConfig movementSpeed(double value) {
        values.put(SharedMonsterAttributes.MOVEMENT_SPEED, value);
        return this;
    }

    /** Sets flying speed. */
    @Doc("Sets the flying movement speed.")
    @Param(name = "value", value = "flying speed factor")
    @Return("this config, for chaining")
    public EntityAttributeConfig flyingSpeed(double value) {
        values.put(SharedMonsterAttributes.FLYING_SPEED, value);
        return this;
    }

    /** Sets follow range. */
    @Doc("Sets the AI follow range in blocks.")
    @Param(name = "value", value = "follow range in blocks")
    @Return("this config, for chaining")
    public EntityAttributeConfig followRange(double value) {
        values.put(SharedMonsterAttributes.FOLLOW_RANGE, value);
        return this;
    }

    /** Sets attack damage. */
    @Doc("Sets the melee attack damage.")
    @Param(name = "value", value = "attack damage in half-hearts")
    @Return("this config, for chaining")
    public EntityAttributeConfig attackDamage(double value) {
        values.put(SharedMonsterAttributes.ATTACK_DAMAGE, value);
        return this;
    }

    /** Sets armor. */
    @Doc("Sets the armor value.")
    @Param(name = "value", value = "armor points")
    @Return("this config, for chaining")
    public EntityAttributeConfig armor(double value) {
        values.put(SharedMonsterAttributes.ARMOR, value);
        return this;
    }

    /** Sets armor toughness. */
    @Doc("Sets the armor toughness.")
    @Param(name = "value", value = "armor toughness")
    @Return("this config, for chaining")
    public EntityAttributeConfig armorToughness(double value) {
        values.put(SharedMonsterAttributes.ARMOR_TOUGHNESS, value);
        return this;
    }

    /** Sets knockback resistance. */
    @Doc("Sets the knockback resistance from 0.0 to 1.0.")
    @Param(name = "value", value = "knockback resistance, 0.0 to 1.0")
    @Return("this config, for chaining")
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
    @Doc("Applies all configured attribute base values onto an entity's attribute map.")
    @Param(name = "map", value = "the attribute map to populate")
    public void applyTo(AbstractAttributeMap map) {
        for (Map.Entry<IAttribute, Double> entry : values.entrySet()) {
            map.registerAttribute(entry.getKey()).setBaseValue(entry.getValue());
        }
    }

    /** Whether nothing has been configured. */
    @Doc("Checks whether no attributes are configured.")
    @Return("true if no attribute was set")
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Register a config under the given entity id (called from EntityTypeBuilderJS.build). */
    @Doc("Registers a config under an entity id (internal; called at entity build time).")
    @Param(name = "id", value = "the entity registry id")
    @Param(name = "config", value = "the config to register; empty configs are ignored")
    public static void register(ResourceLocation id, EntityAttributeConfig config) {
        if (id == null || config == null || config.isEmpty()) {
            return;
        }
        BY_ID.put(id, config);
    }

    /** Look up the config for the given entity id (called from NekoScriptMob.applyEntityAttributes). */
    @Doc("Looks up the registered config for an entity id (internal).")
    @Param(name = "id", value = "the entity registry id")
    @Return("the registered config, or null when none exists")
    public static EntityAttributeConfig get(ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }
}
