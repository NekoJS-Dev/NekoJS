package com.tkisor.nekojs.wrapper.entity;

import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class EntityWrapper {

    @Getter
    protected final Entity raw;

    public EntityWrapper(Entity entity) {
        this.raw = entity;
    }

    public String getId() {
        if (raw == null) return "minecraft:empty";
        if (raw instanceof Player) return "minecraft:player";
        return BuiltInRegistries.ENTITY_TYPE.getKey(raw.getType()).toString();
    }

    public String getName() { return raw.getName().getString(); }
    public double getX() { return raw.getX(); }
    public double getY() { return raw.getY(); }
    public double getZ() { return raw.getZ(); }

    public boolean isPlayer() { return raw instanceof Player; }

    public float getHealth() {
        return raw instanceof LivingEntity le ? le.getHealth() : 0;
    }

    public void setHealth(float health) {
        if (raw instanceof LivingEntity le) le.setHealth(health);
    }

    public float getMaxHealth() {
        return raw instanceof LivingEntity le ? le.getMaxHealth() : 0;
    }
}