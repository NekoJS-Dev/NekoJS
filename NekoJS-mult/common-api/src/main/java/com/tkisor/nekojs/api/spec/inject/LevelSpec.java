package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Level 跨平台统一扩展规范（NF_ONLY——CR 1.12.2 的 World 暂未实现对应扩展）。
 *
 * <p>各 NF 平台的 {@code LevelExtension} 必须 {@code extends LevelSpec}。
 *
 * <p><b>返回类型</b>：声明 {@code Object} 的方法（getBlockState/getPlayers/data）在各平台
 * 用协变返回类型覆盖（NF 返回 {@code BlockState}/{@code List<? extends Player>}/
 * {@code AttachedData<Level>}），spec 不声明具体类型以遵守 common-api 边界约束。
 *
 * <p><b>不</b>在本 spec 中的方法（host-side 便利，参数含 MC 版本特定类型，JS 层不可用）：
 * <ul>
 *   <li>{@code spawnEntity(EntityType, ...)} —— EntityType 是 MC 类型
 *   <li>{@code spawnLightning(...)} —— 委托 spawnEntity
 * </ul>
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface LevelSpec {

    /** 指定坐标的方块状态。 */
    default Object neko$getBlockState(int x, int y, int z) {
        throw new UnsupportedOperationException("LevelSpec.neko$getBlockState not implemented");
    }

    /** 维度 id（如 "minecraft:overworld"）。 */
    default String neko$getId() {
        throw new UnsupportedOperationException("LevelSpec.neko$getId not implemented");
    }

    /**
     * 在指定坐标设置方块。{@code block} 可以是 BlockState、Block 或方块 id 字符串。
     *
     * @return 是否实际修改了方块
     */
    default boolean neko$setBlock(int x, int y, int z, Object block) {
        throw new UnsupportedOperationException("LevelSpec.neko$setBlock not implemented");
    }

    /** 当前世界的白天时间（day time）。 */
    default long neko$getTime() {
        throw new UnsupportedOperationException("LevelSpec.neko$getTime not implemented");
    }

    /** 设置白天时间，仅在服务端世界生效。 */
    default void neko$setTime(long time) {
        throw new UnsupportedOperationException("LevelSpec.neko$setTime not implemented");
    }

    /** 当前世界中的所有玩家。 */
    default Object neko$getPlayers() {
        throw new UnsupportedOperationException("LevelSpec.neko$getPlayers not implemented");
    }

    /** 当前是否正在下雨。 */
    default boolean neko$isRaining() {
        throw new UnsupportedOperationException("LevelSpec.neko$isRaining not implemented");
    }

    /** 设置下雨状态，仅在服务端世界生效。 */
    default void neko$setRaining(boolean raining) {
        throw new UnsupportedOperationException("LevelSpec.neko$setRaining not implemented");
    }

    /** 当前是否为白天。 */
    default boolean neko$isDay() {
        throw new UnsupportedOperationException("LevelSpec.neko$isDay not implemented");
    }

    /** 挂载到该 level 的内存数据容器。 */
    default Object neko$data() {
        throw new UnsupportedOperationException("LevelSpec.neko$data not implemented");
    }
}
