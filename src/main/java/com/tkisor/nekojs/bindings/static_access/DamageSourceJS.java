package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * {@code DamageSource} 工厂 helper（绑定全局 {@code DamageSource}，成员委托
 * {@link DamageSource} 类——该类没有静态工厂，{@code of} 系列全部走本 helper）。
 *
 * <p>{@code DamageSource.of(level, 'minecraft:explosion')}——伤害类型 id 经
 * {@code Identifier} 适配器自动从字符串转换。
 */
public class DamageSourceJS {

    /** 从注册表 id（如 {@code "minecraft:explosion"}）解析伤害类型并构造 DamageSource。 */
    public DamageSource of(Level level, Identifier damageTypeId) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        return level.damageSources().source(key);
    }

    /** 指定直接来源实体的伤害（如生物近战）。 */
    public DamageSource of(Level level, Identifier damageTypeId, Entity causingEntity) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        return level.damageSources().source(key, causingEntity);
    }

    public DamageSource generic(Level level) {
        return level.damageSources().genericKill();
    }

    public DamageSource magic(Level level) {
        return level.damageSources().magic();
    }

    public DamageSource explosion(Level level) {
        return level.damageSources().explosion(null);
    }

    public DamageSource drowning(Level level) {
        return level.damageSources().drowning();
    }

    public DamageSource fall(Level level) {
        return level.damageSources().fall();
    }

    public DamageSource lava(Level level) {
        return level.damageSources().lava();
    }

    public DamageSource lightning(Level level) {
        return level.damageSources().lightningBolt();
    }

    public DamageSource starvation(Level level) {
        return level.damageSources().starvation();
    }

    public DamageSource outOfWorld(Level level) {
        return level.damageSources().fellOutOfWorld();
    }
}
