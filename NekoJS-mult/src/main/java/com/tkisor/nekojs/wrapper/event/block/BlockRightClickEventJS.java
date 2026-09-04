package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玩家右键方块事件（{@code BlockEvents.rightClicked}）的加载器中立载荷。
 *
 * <p>NeoForge 侧直传原生 {@code PlayerInteractEvent.RightClickBlock}（成员同形：
 * getPlayer/getLevel/getPos/getHand，state 由 pos 解析）；fabric 侧由
 * {@code FabricBlockEventBindings} 从 {@code UseBlockCallback} 转换。
 *
 * <p>可取消：监听器返回 {@code true} 时，NeoForge 侧取消原生事件，fabric 侧回调返回
 * {@code SUCCESS}（跳过后续交互处理）。仅服务端触发（绑定处过滤客户端交互）。
 */
@Doc("Fired when a player right-clicks a block (BlockEvents.rightClicked).")
@Doc("Return true to consume the interaction.")
@Getter
public class BlockRightClickEventJS {

    @Doc("The clicking player.")
    private final Player player;

    @Doc("The level the interaction happened in.")
    private final Level level;

    @Doc("Position of the clicked block.")
    private final BlockPos pos;

    @Doc("Block state of the clicked block.")
    private final BlockState state;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    public BlockRightClickEventJS(Player player, Level level, BlockPos pos,
                                  BlockState state, InteractionHand hand) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.hand = hand;
    }

    @Doc("The block that was clicked.")
    public net.minecraft.world.level.block.Block getBlock() {
        return state.getBlock();
    }
}
