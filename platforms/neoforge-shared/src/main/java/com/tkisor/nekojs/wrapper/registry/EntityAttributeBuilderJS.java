package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class EntityAttributeBuilderJS {
    private double maxHealth = 20.0;
    private double movementSpeed = 0.25;
    private double followRange = 16.0;
    private double attackDamage = 2.0;
    private double armor = 0.0;
    private double armorToughness = 0.0;
    private double knockbackResistance = 0.0;

    public EntityAttributeBuilderJS maxHealth(double value) {
        this.maxHealth = value;
        return this;
    }

    public EntityAttributeBuilderJS movementSpeed(double value) {
        this.movementSpeed = value;
        return this;
    }

    public EntityAttributeBuilderJS followRange(double value) {
        this.followRange = value;
        return this;
    }

    public EntityAttributeBuilderJS attackDamage(double value) {
        this.attackDamage = value;
        return this;
    }

    public EntityAttributeBuilderJS armor(double value) {
        this.armor = value;
        return this;
    }

    public EntityAttributeBuilderJS armorToughness(double value) {
        this.armorToughness = value;
        return this;
    }

    public EntityAttributeBuilderJS knockbackResistance(double value) {
        this.knockbackResistance = value;
        return this;
    }

    public AttributeSupplier build() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FOLLOW_RANGE, followRange)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, armorToughness)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance)
                .build();
    }
}
