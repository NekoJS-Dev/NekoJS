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
import net.minecraft.world.entity.MobSpawnType;
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

    /** 当前世界的白天时间（day time）。 */
    default long neko$getTime() {
        return self().getDayTime();
    }

    /**
     * 设置白天时间，仅在 {@link ServerLevel} 上生效。
     * 客户端世界调用为空操作。
     */
    default void neko$setTime(long time) {
        if (self() instanceof ServerLevel serverLevel) {
            serverLevel.setDayTime(time);
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
     * 设置下雨状态，仅在 {@link ServerLevel} 上生效（通过重置天气参数实现）。
     * 客户端世界调用为空操作。
     */
    default void neko$setRaining(boolean raining) {
        if (self() instanceof ServerLevel serverLevel) {
            // (clearTime, rainTime, raining, thundering)
            serverLevel.setWeatherParameters(0, raining ? 600 : 0, raining, false);
        }
    }

    /** 当前是否为白天。 */
    default boolean neko$isDay() {
        return self().isDay();
    }

    /** 返回挂载到该 level 的内存数据容器；首次访问时 lazy 创建并触发 {@code attachLevelData}。 */
    AttachedData<Level> neko$data();
}