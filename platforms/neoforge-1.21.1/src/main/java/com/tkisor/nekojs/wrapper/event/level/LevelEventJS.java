package com.tkisor.nekojs.wrapper.event.level;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

import java.util.List;

/**
 * 统一的关卡（世界）事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.level} 在 NeoForge（21.1/26.x）与 Cleanroom 上一致可用
 * （Cleanroom 的 {@code World} 同样映射到 {@code level} 字段名）。
 *
 * <p>跨平台可移植性说明：
 * <ul>
 *   <li>{@code explosion} 统一为 {@code Object}：NeoForge 26.x 为 {@code ServerExplosion}，
 *       NeoForge 21.1 为 {@code Explosion}，Cleanroom 为 {@code Explosion}。具体类型由脚本侧
 *       按平台判断，多数脚本只用 {@code affectedBlocks}/{@code affectedEntities}。</li>
 *   <li>{@code level} 类型为 {@code LevelAccessor}（NeoForge 事件均返回该类型或子类 Level）；
 *       Cleanroom 用 {@code World}（见 cleanroom 平台的独立 wrapper）。</li>
 * </ul>
 *
 * <p>所有 LevelEvents 均为非分发（Void key）。
 */
@Getter
public class LevelEventJS {
    private final LevelAccessor level;
    // 事件特定字段（nullable）
    private Object explosion;
    private List<BlockPos> affectedBlocks;
    private List<Entity> affectedEntities;

    public LevelEventJS(LevelAccessor level) {
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
