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
 * Fabric bridge for block events: broken (already present) plus rightClicked / leftClicked
 * (26.x fabric-api {@code UseBlockCallback} / {@code AttackBlockCallback}, neither deprecated,
 * both fired on server and client; the binding filters client interactions so the SERVER bus
 * only sees server instances).
 *
 * <p>26.x platform fact: fabric-api removed PlayerBlockPlaceEvents (placed / entityPlaced /
 * entityMultiPlaced have no ready callback), so those wait for a mixin pass. Fabric's
 * AttackBlockCallback only fires in survival mode (NeoForge fires in all modes); the difference
 * is documented in the payload javadoc.
 */
public final class FabricBlockEventBindings {

    private FabricBlockEventBindings() {}

    /** Right-click on a block, dispatched by Block; cancellable. A script returns true to consume
     * the interaction, which maps to SUCCESS. */
    private static final EventBusJS<BlockRightClickEventJS, Block> RIGHT_CLICKED =
            BlockEvents.GROUP.add("rightClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockRightClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())));

    /** Left-click on a block, dispatched by Block; cancellable. A script returns true to consume
     * the interaction, which maps to SUCCESS. */
    private static final EventBusJS<BlockLeftClickEventJS, Block> LEFT_CLICKED =
            BlockEvents.GROUP.add("leftClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockLeftClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())));

    public static void register() {
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        bridge.bindCancellable(
                BlockEvents.BROKEN,
                listener -> PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                        !listener.test(new BlockBrokenEventJS(level, pos, state, player))));
        bridge.bindCancellableDispatched(
                RIGHT_CLICKED,
                listener -> UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
                    if (level.isClientSide()) {
                        return InteractionResult.PASS;
                    }
                    var state = level.getBlockState(hit.getBlockPos());
                    boolean cancelled = listener.apply(
                            new BlockRightClickEventJS(player, level, hit.getBlockPos(), state, hand),
                            state.getBlock());
                    return cancelled ? InteractionResult.SUCCESS : InteractionResult.PASS;
                }));
        bridge.bindCancellableDispatched(
                LEFT_CLICKED,
                listener -> AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
                    if (level.isClientSide()) {
                        return InteractionResult.PASS;
                    }
                    var state = level.getBlockState(pos);
                    boolean cancelled = listener.apply(
                            new BlockLeftClickEventJS(player, level, pos, state, hand), state.getBlock());
                    return cancelled ? InteractionResult.SUCCESS : InteractionResult.PASS;
                }));
    }
}
