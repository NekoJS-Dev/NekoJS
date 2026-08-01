package com.tkisor.nekojs.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/**
 * 方块随机 tick 事件（{@code BlockEvents.randomTick}）。
 *
 * <p>原版没有对应的 NeoForge 事件；由 {@code BlockBehaviourMixin} 在
 * {@code BlockBehaviour.randomTick} HEAD 注入并 post 到 {@code NeoForge.EVENT_BUS}。
 * 仅对 {@code isRandomlyTicking()} 为 true 的方块触发（对标原版/ KubeJS 语义）。
 */
public class RandomTickEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final RandomSource random;

    public RandomTickEvent(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.random = random;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    public RandomSource getRandom() { return random; }
    public Block getBlock() { return state.getBlock(); }
}
