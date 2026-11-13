// 26.x implementation; no version guard should appear in this file again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; keep it in sync when changing behavior
// here.
package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.LevelSpec;
import com.tkisor.nekojs.platform.compat.McVersionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

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
        return neko$spawnEntity(McVersionCompat.get().lightningBoltType(), x, y, z);
    }

    default String neko$getId() {
        return self().dimension().identifier().toString();
    }

    /**
     * Sets the block at the given position. {@code block} can be a {@link Block}, a
     * {@link BlockState} or a block id string. It uses flag {@code 3}
     * ({@code Block.UPDATE_ALL}), which syncs the client and notifies neighbors.
     *
     * @return whether the block actually changed
     */
    default boolean neko$setBlock(int x, int y, int z, Object block) {
        BlockState state = null;
        if (block instanceof BlockState bs) {
            state = bs;
        } else if (block instanceof Block b) {
            state = b.defaultBlockState();
        } else if (block instanceof String id) {
            var loc = Identifier.tryParse(id);
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
     * The day time of the current world.
     * <p>26.x removed {@code getDayTime}, so this reads the overworld clock
     * ({@link Level#getOverworldClockTime()}).
     */
    default long neko$getTime() {
        return self().getOverworldClockTime();
    }

    /**
     * Sets the day time. It only works on a {@link ServerLevel} (through {@code setTotalTicks} on
     * the overworld clock). A call on a client world is a no-op.
     */
    default void neko$setTime(long time) {
        if (self() instanceof ServerLevel serverLevel) {
            var clockManager = serverLevel.getServer().clockManager();
            serverLevel.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
                    .get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
                    .ifPresent(clock -> clockManager.setTotalTicks(clock, time));
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
     * {@code 1.0} means raining and {@code 0.0} means clear. The API works on both client and
     * server worlds.
     */
    default void neko$setRaining(boolean raining) {
        self().setRainLevel(raining ? 1.0F : 0.0F);
    }

    /**
     * Whether it is currently day.
     * <p>26.x removed {@code Level.isDay()}, so this derives it from the overworld clock using the
     * classic day/night cycle: a point counts as day when it falls in {@code [0, 13000)} of
     * {@code time % 24000}.
     */
    default boolean neko$isDay() {
        long time = self().getOverworldClockTime();
        return (time % 24000L) < 13000L;
    }

    /**
     * Returns the in-memory data container attached to this level. The first access creates it
     * lazily and fires {@code attachLevelData}.
     */
    AttachedData<Level> neko$data();
}
