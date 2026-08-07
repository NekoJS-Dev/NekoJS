package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.LevelSpec;
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
        return neko$spawnEntity(EntityType.LIGHTNING_BOLT, x, y, z);
    }

    default String neko$getId() {
        return self().dimension().identifier().toString();
    }

    /**
     * 在指定坐标设置方块。{@code block} 可以是 {@link Block}、{@link BlockState} 或方块 id 字符串。
     * 使用 flag {@code 3}（{@code Block.UPDATE_ALL}）：同步客户端 + 通知邻居。
     *
     * @return 是否实际修改了方块
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
     * 当前世界的白天时间（day time）。
     * <p>26.x 移除了 {@code getDayTime}，这里改用主世界时钟（{@link Level#getOverworldClockTime()}）。
     */
    default long neko$getTime() {
        return self().getOverworldClockTime();
    }

    /**
     * 设置白天时间，仅在 {@link ServerLevel} 上生效（通过主世界时钟 {@code setTotalTicks} 实现）。
     * 客户端世界调用为空操作。
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

    /** 当前世界中的所有玩家。 */
    default List<? extends Player> neko$getPlayers() {
        return self().players();
    }

    /** 当前是否正在下雨。 */
    default boolean neko$isRaining() {
        return self().isRaining();
    }

    /**
     * 设置下雨状态。
     * <p>26.x 移除了 {@code setWeatherParameters}，这里直接设置雨量等级：{@code 1.0} 表示下雨，
     * {@code 0.0} 表示停止。该 API 对客户端/服务端世界均可用。
     */
    default void neko$setRaining(boolean raining) {
        self().setRainLevel(raining ? 1.0F : 0.0F);
    }

    /**
     * 当前是否为白天。
     * <p>26.x 移除了 {@code Level.isDay()}，这里基于主世界时钟按经典昼夜周期推导：
     * {@code time % 24000} 落在 {@code [0, 13000)} 视为白天。
     */
    default boolean neko$isDay() {
        long time = self().getOverworldClockTime();
        return (time % 24000L) < 13000L;
    }

    /** 返回挂载到该 level 的内存数据容器；首次访问时 lazy 创建并触发 {@code attachLevelData}。 */
    AttachedData<Level> neko$data();
}
