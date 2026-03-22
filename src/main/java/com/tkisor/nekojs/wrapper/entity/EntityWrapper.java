package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.wrapper.NekoWrapper;
import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class EntityWrapper implements NekoWrapper<Entity> {
    @Getter
    protected final Entity raw;

    public EntityWrapper(Entity entity) {
        this.raw = entity;
    }

    public static EntityWrapper of(Entity entity) {
        return switch (entity) {
            case null -> null;
            case Player p -> new PlayerWrapper(p);
            case LivingEntity le -> new LivingEntityWrapper(le);
            default -> new EntityWrapper(entity);
        };
    }

    public Level level() {
        return raw.level();
    }

    public MinecraftServer getServer() {
        return raw.level().getServer();
    }

    public String getId() {
        if (raw == null) return "minecraft:empty";
        return BuiltInRegistries.ENTITY_TYPE.getKey(raw.getType()).toString();
    }

    public String getName() {
        return raw.getName().getString();
    }

    public double getX() {
        return raw.getX();
    }

    public double getY() {
        return raw.getY();
    }

    public double getZ() {
        return raw.getZ();
    }

    public Vec3 getPos() {
        return raw.position();
    }

    public void setOnFire(int seconds) {
        raw.igniteForSeconds(seconds);
    }

    public boolean isAlive() {
        return raw.isAlive();
    }

    public boolean isSneaking() {
        return raw.isCrouching();
    }

    public boolean isSprinting() {
        return raw.isSprinting();
    }

    public boolean isInWater() {
        return raw.isInWater();
    }

    public boolean isOnGround() {
        return raw.onGround();
    }

    public void addTag(String tag) {
        raw.addTag(tag);
    }

    public void removeTag(String tag) {
        raw.removeTag(tag);
    }

    public boolean hasTag(String tag) {
        return raw.entityTags().contains(tag);
    }

    public Set<String> getTags() {
        return raw.entityTags();
    }

    public void setMotion(double x, double y, double z) {
        raw.setDeltaMovement(new Vec3(x, y, z));
        raw.hurtMarked = true;
    }

    public Vec3 getMotion() {
        return raw.getDeltaMovement();
    }

    public void kill() {
        if (raw.level() instanceof ServerLevel serverLevel) {
            raw.kill(serverLevel);
        }
    }

    public void discard() {
        raw.discard();
    }

    @Override
    public Entity unwrap() {
        return raw;
    }
}