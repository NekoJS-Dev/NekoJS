package com.tkisor.nekojs.wrapper.event.block;

import lombok.Getter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;

/**
 * 统一的方块事件 wrapper（跨平台字段名一致）。
 *
 * <p>1.12.2 版本：字段类型用 1.12.2 原生类（{@code World}/{@code IBlockState}/{@code EntityPlayer}），
 * 但 getter 名与 NeoForge 一致。脚本侧 {@code event.level}（不是 {@code world}）、
 * {@code event.player}、{@code event.pos}、{@code event.state}、{@code event.block} 跨平台一致。
 */
@Getter
public class BlockEventJS {
    private final World level;
    private final BlockPos pos;
    private final IBlockState state;
    private final Block block;
    private final EntityPlayer player;
    private ItemStack item;
    private EnumHand hand;
    private Entity entity;
    private Float fallDistance;
    private Integer expToDrop;

    public BlockEventJS(World level, BlockPos pos, IBlockState state, Block block, EntityPlayer player) {
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

    public BlockEventJS withItem(ItemStack item, EnumHand hand) {
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
