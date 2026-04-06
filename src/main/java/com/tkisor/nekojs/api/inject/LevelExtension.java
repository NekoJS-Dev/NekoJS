package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface LevelExtension {

    private Level self() {
        return (Level) this;
    }

    default BlockState neko$getBlockState(int x, int y, int z) {
        return self().getBlockState(new BlockPos(x, y, z));
    }

    default Entity neko$spawnEntity(EntityType<?> type, double x, double y, double z) {
        if (self() instanceof ServerLevel serverLevel) {
            var entity = type.create(serverLevel, EntitySpawnReason.EVENT);
            if (entity == null) {
                return null;
            }

            entity.setPos(x, y, z);
            serverLevel.addFreshEntity(entity);
            return entity;
        }
        return null;
    }

    default Entity neko$spawnLightning(double x, double y, double z) {
        return neko$spawnEntity(EntityType.LIGHTNING_BOLT, x, y, z);
    }
}
