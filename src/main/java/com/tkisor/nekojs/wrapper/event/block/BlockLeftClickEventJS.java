package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玩家左键方块事件（{@code BlockEvents.leftClicked}）的加载器中立载荷。
 *
 * <p>NeoForge 侧直传原生 {@code PlayerInteractEvent.LeftClickBlock}（成员同形）；
 * fabric 侧由 {@code FabricBlockEventBindings} 从 {@code AttackBlockCallback} 转换。
 * fabric 已知语义差异：fabric-api 的 AttackBlockCallback 只在<b>生存模式</b>触发
 *（creative 模式不触发——NeoForge 左侧全部模式都触发）。
 *
 * <p>可取消：监听器返回 {@code true} 时，NeoForge 侧取消原生事件，fabric 侧回调返回
 * {@code SUCCESS}（跳过攻击处理）。仅服务端触发（绑定处过滤客户端交互）。
 */
@Doc("Fired when a player left-clicks a block (BlockEvents.leftClicked).")
@Doc("Return true to consume the attack interaction.")
@Getter
public class BlockLeftClickEventJS {

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

    public BlockLeftClickEventJS(Player player, Level level, BlockPos pos,
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
