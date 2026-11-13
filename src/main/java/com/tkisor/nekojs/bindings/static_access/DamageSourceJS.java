package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Factory helper for {@code DamageSource} (bound as the global {@code DamageSource}, with its
 * members delegating to the {@link DamageSource} class — that class has no static factories, so
 * every {@code of} overload goes through this helper).
 *
 * <p>{@code DamageSource.of(level, 'minecraft:explosion')} — the damage type id is converted from a
 * string automatically by the {@code Identifier} adapter.
 */
public class DamageSourceJS {

    /** Resolves a damage type from its registry id (for example {@code "minecraft:explosion"}) and
     * builds a DamageSource. */
    public DamageSource of(Level level, Identifier damageTypeId) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        return level.damageSources().source(key);
    }

    /** Damage from the given direct source entity, such as a mob melee attack. */
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

    // Convenience methods such as drowning()/starvation() on DamageSources differ between versions, so
    // every case builds from a DamageTypes key: same semantics and available on all versions.
    public DamageSource drowning(Level level) {
        return level.damageSources().source(DamageTypes.DROWN);
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
        return level.damageSources().source(DamageTypes.STARVE);
    }

    public DamageSource outOfWorld(Level level) {
        return level.damageSources().fellOutOfWorld();
    }
}
