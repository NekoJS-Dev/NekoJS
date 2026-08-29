package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 实体属性（attribute supplier）builder：流式设置常用属性后 {@code build()}。
 */
@Doc("Builder for a mob's attribute supplier, covering the common vanilla attributes.")
@Doc("Every setter defaults to the vanilla mob baseline (health 20, speed 0.25, and so on).")
public class EntityAttributeBuilderJS {
    private double maxHealth = 20.0;
    private double movementSpeed = 0.25;
    private double followRange = 16.0;
    private double attackDamage = 2.0;
    private double armor = 0.0;
    private double armorToughness = 0.0;
    private double knockbackResistance = 0.0;

    /** 最大生命值（默认 20）。 */
    @Doc("Sets the max health.")
    @Param(name = "value", value = "max health in half-hearts; vanilla baseline is 20")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS maxHealth(double value) {
        this.maxHealth = value;
        return this;
    }

    /** 移动速度（默认 0.25）。 */
    @Doc("Sets the movement speed.")
    @Param(name = "value", value = "movement speed; vanilla mob baseline is 0.25")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS movementSpeed(double value) {
        this.movementSpeed = value;
        return this;
    }

    /** 追踪/感知范围（默认 16）。 */
    @Doc("Sets the follow range.")
    @Param(name = "value", value = "follow range in blocks; vanilla baseline is 16")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS followRange(double value) {
        this.followRange = value;
        return this;
    }

    /** 近战攻击伤害（默认 2）。 */
    @Doc("Sets the attack damage.")
    @Param(name = "value", value = "melee attack damage; vanilla baseline is 2")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS attackDamage(double value) {
        this.attackDamage = value;
        return this;
    }

    /** 护甲值（默认 0）。 */
    @Doc("Sets the armor value.")
    @Param(name = "value", value = "armor points; vanilla baseline is 0")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS armor(double value) {
        this.armor = value;
        return this;
    }

    /** 护甲韧性（默认 0）。 */
    @Doc("Sets the armor toughness.")
    @Param(name = "value", value = "armor toughness; vanilla baseline is 0")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS armorToughness(double value) {
        this.armorToughness = value;
        return this;
    }

    /** 击退抗性（0-1，默认 0）。 */
    @Doc("Sets the knockback resistance.")
    @Param(name = "value", value = "knockback resistance between 0 and 1; vanilla baseline is 0")
    @Return("this, for chaining")
    public EntityAttributeBuilderJS knockbackResistance(double value) {
        this.knockbackResistance = value;
        return this;
    }

    /** 构建属性 supplier（{@link Mob#createMobAttributes()} 基线 + 全部已设属性）。 */
    @Doc("Builds the attribute supplier from all configured values.")
    @Return("an AttributeSupplier based on the vanilla mob baseline with this builder's values applied")
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
