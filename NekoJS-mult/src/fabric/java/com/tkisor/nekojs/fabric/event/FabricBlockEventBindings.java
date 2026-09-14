package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;

/**
 * 方块事件面的 fabric 桥：broken（已有）与 rightClicked / leftClicked（26.x fabric-api
 * 的 UseBlockCallback / AttackBlockCallback，均未弃用、serverside+client 双端回调——
 * 绑定处过滤客户端交互，SERVER 总线只收服务端实例）。
 *
 * <p>26.x 平台事实：PlayerBlockPlaceEvents 已从 fabric-api 删除（placed / entityPlaced /
 * entityMultiPlaced 无现成回调），留 mixin 批次。fabric 的 AttackBlockCallback 只在
 * 生存模式触发（NeoForge 侧全模式），语义差异记入载荷 javadoc。
 */
public final class FabricBlockEventBindings {

    private FabricBlockEventBindings() {}

    /** 右键方块（按 Block 分发；可取消——脚本 return true = consume，对应 SUCCESS）。 */
    private static final EventBusJS<BlockRightClickEventJS, Block> RIGHT_CLICKED =
            BlockEvents.GROUP.add("rightClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockRightClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())));

    /** 左键方块（按 Block 分发；可取消——脚本 return true = consume，对应 SUCCESS）。 */
    private static final EventBusJS<BlockLeftClickEventJS, Block> LEFT_CLICKED =
            BlockEvents.GROUP.add("leftClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockLeftClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())));

    public static void register() {
        // Fabric 的 BEFORE 事件返回 false = 取消破坏，正好对应 NekoJS 脚本 return false
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !BlockEvents.BROKEN.post(new BlockBrokenEventJS(level, pos, state, player)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            boolean cancelled = RIGHT_CLICKED.post(
                    new BlockRightClickEventJS(player, level, hit.getBlockPos(),
                            level.getBlockState(hit.getBlockPos()), hand));
            return cancelled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            boolean cancelled = LEFT_CLICKED.post(
                    new BlockLeftClickEventJS(player, level, pos, level.getBlockState(pos), hand));
            return cancelled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
