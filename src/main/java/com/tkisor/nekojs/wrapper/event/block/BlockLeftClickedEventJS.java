package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.wrapper.block.BlockWrapper;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class BlockLeftClickedEventJS implements NekoCancellableEvent {

    private final PlayerInteractEvent.LeftClickBlock rawEvent;
    /**
     * -- GETTER --
     *  获取被点击的方块状态
     *  JS 侧: event.block
     */
    @Getter
    private final BlockWrapper block;

    public BlockLeftClickedEventJS(PlayerInteractEvent.LeftClickBlock rawEvent) {
        this.rawEvent = rawEvent;

        Level level = rawEvent.getLevel();
        BlockPos pos = rawEvent.getPos();
        BlockState state = level.getBlockState(pos);
        this.block = new BlockWrapper(level, pos, state);
    }

    /**
     * 获取点击的玩家
     * JS 侧: event.player
     */
    public PlayerWrapper getPlayer() {
        return new PlayerWrapper(rawEvent.getEntity());
    }

    public String getBlockId() {
        return this.block.getId();
    }

    /**
     * 获取方块坐标
     * JS 侧: event.pos
     */
    public BlockPos getPos() {
        return rawEvent.getPos();
    }

    /**
     * 获取点击的面 (上、下、东、西等)
     * JS 侧: event.face
     */
    public Direction getFace() {
        return rawEvent.getFace();
    }

}