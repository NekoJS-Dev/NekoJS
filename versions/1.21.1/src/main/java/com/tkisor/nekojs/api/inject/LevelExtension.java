// 1.21.1 implementation, paired with the file of the same name under the version tree's src/
// that targets 26.x. Its content equals that file as evaluated for this node.
// Update this file when behavior changes on the 26.x side.
package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.LevelSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import net.minecraft.world.entity.MobSpawnType;

/**
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface LevelExtension extends LevelSpec {

    private Level self() {
        return (Level) this;
    }

    default BlockState neko$getBlockState(int x, int y, int z) {
        return self().getBlockState(new BlockPos(x, y, z));
    }

    default Entity neko$spawnEntity(EntityType<?> type, double x, double y, double z) {
        if (self() instanceof ServerLevel serverLevel) {
            var entity = type.create(serverLevel, null, BlockPos.containing(x, y, z), MobSpawnType.EVENT, true, false);
            if (entity == null) {
                return null;
            }

            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            serverLevel.addFreshEntity(entity);
            return entity;
        }
        return null;
    }

    default Entity neko$spawnLightning(double x, double y, double z) {
        return neko$spawnEntity(EntityType.LIGHTNING_BOLT, x, y, z);
    }

    default String neko$getId() {
        return self().dimension().location().toString();
    }

    /**
     * Sets the block at the given position. {@code block} can be a {@link Block}, a
     * {@link BlockState} or a block id string. It uses flag {@code 3}
     * ({@code Block.UPDATE_ALL}), which syncs the client and notifies neighbors.
     *
     * @return whether the block was actually changed
     */
    default boolean neko$setBlock(int x, int y, int z, Object block) {
        BlockState state = null;
        if (block instanceof BlockState bs) {
            state = bs;
        } else if (block instanceof Block b) {
            state = b.defaultBlockState();
        } else if (block instanceof String id) {
            var loc = ResourceLocation.tryParse(id);
            if (loc != null) {
                state = BuiltInRegistries.BLOCK.getOptional(loc)
                        .map(Block::defaultBlockState)
                        .orElse(null);
            }
        }
        if (state == null) {
            return false;
        }
        return self().setBlock(new BlockPos(x, y, z), state, 3);
    }

    /**
     * Day time of the current world.
     * <p>26.x removed {@code getDayTime}, so this reads the overworld clock
     * ({@link Level#getOverworldClockTime()}) instead.
     */
    default long neko$getTime() {
        return self().getDayTime();
    }

    /**
     * Sets the day time. Only effective on a {@link ServerLevel}, implemented through the
     * overworld clock's {@code setTotalTicks}. A call on a client world does nothing.
     */
    default void neko$setTime(long time) {
        if (self() instanceof ServerLevel serverLevel) {
            serverLevel.setDayTime(time);
        }
    }

    /** All players in the current world. */
    default List<? extends Player> neko$getPlayers() {
        return self().players();
    }

    /** Whether it is currently raining. */
    default boolean neko$isRaining() {
        return self().isRaining();
    }

    /**
     * Sets the rain state.
     * <p>26.x removed {@code setWeatherParameters}, so this sets the rain level directly:
     * {@code 1.0} starts rain and {@code 0.0} stops it. This API works on both client and
     * server worlds.
     */
    default void neko$setRaining(boolean raining) {
        if (self() instanceof ServerLevel serverLevel) {
            // (clearTime, rainTime, raining, thundering)
            serverLevel.setWeatherParameters(0, raining ? 600 : 0, raining, false);
        }
    }

    /**
     * Whether it is currently day.
     * <p>26.x removed {@code Level.isDay()}, so this derives it from the overworld clock using
     * the classic day/night cycle: {@code time % 24000} in {@code [0, 13000)} counts as day.
     */
    default boolean neko$isDay() {
        return self().isDay();
    }

    /** Returns the in-memory data container attached to this level. The first access creates it
     * lazily and triggers {@code attachLevelData}. */
    AttachedData<Level> neko$data();
}
