package com.tkisor.nekojs.wrapper.event.level;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;

import java.util.List;

/**
 * 统一的关卡（世界）事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.level} 在 NeoForge（21.1/26.x）与 Cleanroom 上一致可用
 * （Cleanroom 的 {@code World} 同样映射到 {@code level} 字段名）。
 *
 * <p>跨平台可移植性说明：
 * <ul>
 *   <li>{@code explosion} 统一为 {@code Object}：Cleanroom 为 {@code Explosion}，
 *       NeoForge 26.x 为 {@code ServerExplosion}，NeoForge 21.1 为 {@code Explosion}。</li>
 * </ul>
 *
 * <p>所有 LevelEvents 均为非分发（Void key）。
 */
@Getter
public class LevelEventJS {
    private final World level;
    // 事件特定字段（nullable）
    private Object explosion;
    private List<BlockPos> affectedBlocks;
    private List<Entity> affectedEntities;

    public LevelEventJS(World level) {
        this.level = level;
    }

    public LevelEventJS withExplosion(Object explosion) {
        this.explosion = explosion;
        return this;
    }

    public LevelEventJS withDetonate(List<BlockPos> affectedBlocks, List<Entity> affectedEntities) {
        this.affectedBlocks = affectedBlocks;
        this.affectedEntities = affectedEntities;
        return this;
    }
}
