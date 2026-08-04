package com.tkisor.nekojs.wrapper.event.block;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;

/**
 * 统一的方块事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.player}、{@code event.level}、{@code event.pos}、{@code event.state}、
 * {@code event.block} 在 NeoForge 与 Cleanroom 上一致可用。事件特定字段（如 {@code expToDrop}、
 * {@code item}、{@code entity}）仅在对应事件上非 null。
 *
 * <p>统一用 {@code level}（MC 1.13+ 规范名），不暴露 {@code world}。
 */
@Getter
public class BlockEventJS {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;
    private final Block block;
    private final Player player;
    // 事件特定字段（nullable）
    private Integer expToDrop;
    private ItemStack item;
    private InteractionHand hand;
    private Entity entity;
    private Float fallDistance;

    public BlockEventJS(LevelAccessor level, BlockPos pos, BlockState state, Block block, Player player) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.block = block;
        this.player = player;
    }

    public BlockEventJS withExpToDrop(int expToDrop) {
        this.expToDrop = expToDrop;
        return this;
    }

    public BlockEventJS withItem(ItemStack item, InteractionHand hand) {
        this.item = item;
        this.hand = hand;
        return this;
    }

    public BlockEventJS withEntity(Entity entity) {
        this.entity = entity;
        return this;
    }

    public BlockEventJS withFallDistance(float fallDistance) {
        this.fallDistance = fallDistance;
        return this;
    }
}
