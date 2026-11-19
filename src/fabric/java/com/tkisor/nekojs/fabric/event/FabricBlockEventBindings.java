package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric bridge for block events: broken plus rightClicked / leftClicked
 * (26.x fabric-api {@code UseBlockCallback} / {@code AttackBlockCallback}, neither deprecated,
 * both fired on server and client; the binding filters client interactions so the SERVER bus
 * only sees server instances).
 *
 * <p>Stage gates run before script event listeners so locked block breaks, block interactions,
 * and held-item uses/attacks are deterministically denied first.
 */
public final class FabricBlockEventBindings {

    private FabricBlockEventBindings() {}

    /** Right-click on a block, dispatched by Block; cancellable. A script returns true to consume
     * the interaction, which maps to SUCCESS. */
    private static final EventBusJS<BlockRightClickEventJS, Block> RIGHT_CLICKED =
            BlockEvents.GROUP.add("rightClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockRightClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())), api -> {
                api.versioned().when(event -> !(event instanceof StableRightClick));
                api.stable()
                        .when(event -> event instanceof StableRightClick)
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockRightClick)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Left-click on a block, dispatched by Block; cancellable. A script returns true to consume
     * the interaction, which maps to SUCCESS. */
    private static final EventBusJS<BlockLeftClickEventJS, Block> LEFT_CLICKED =
            BlockEvents.GROUP.add("leftClicked", ScriptType.SERVER, EventBusJS.of(
                    BlockLeftClickEventJS.class, true,
                    DispatchKey.of(Block.class, event -> event.getBlock())), api -> {
                api.versioned().when(event -> !(event instanceof StableLeftClick));
                api.stable()
                        .when(event -> event instanceof StableLeftClick)
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockLeftClick)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static boolean postEligibleRightClick(ServerPlayer player, Level level, BlockPos pos,
                                                 BlockState state, InteractionHand hand) {
        return RIGHT_CLICKED.hasStableModuleListeners(ScriptType.SERVER) && ServerLevelEventThread.isCurrent(level)
                && RIGHT_CLICKED.postStableModuleCancellable(
                        new StableRightClick(player, level, pos, state, hand), state.getBlock(), ScriptType.SERVER);
    }

    public static boolean postStableLeftClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!LEFT_CLICKED.hasStableModuleListeners(ScriptType.SERVER) || !ServerLevelEventThread.isCurrent(level)) return false;
        BlockState state = level.getBlockState(pos);
        return LEFT_CLICKED.postStableModuleCancellable(
                new StableLeftClick(player, level, pos, state), state.getBlock(), ScriptType.SERVER);
    }

    private static final class StableLeftClick extends BlockLeftClickEventJS {
        private StableLeftClick(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
            super(player, level, pos, state, InteractionHand.MAIN_HAND);
        }
    }

    private static final class StableRightClick extends BlockRightClickEventJS {
        private StableRightClick(ServerPlayer player, Level level, BlockPos pos, BlockState state, InteractionHand hand) {
            super(player, level, pos, state, hand);
        }
    }

    /** Initializes v1 and v2 block event buses into {@code BlockEvents.GROUP} without registering callbacks. */
    public static void bootstrap() {
        FabricBlockEventBindingsV2.bootstrap();
    }

    /** Registers Fabric event callbacks onto the corresponding NekoJS script event buses. */
    public static void register() {
        FabricBlockEventBindingsV2.bootstrap();
        FabricEventBusBridge bridge = FabricEventBusBridge.create();
        bridge.bindCancellable(
                BlockEvents.BROKEN,
                listener -> PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
                    if (!StageGateEnforcer.allowBreakBlock(player, level, pos, state)) {
                        return false;
                    }
                    return !listener.test(new BlockBrokenEventJS(level, pos, state, player));
                }));
        bridge.bindCancellableDispatched(
                RIGHT_CLICKED,
                listener -> UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
                    if (level.isClientSide()) {
                        return InteractionResult.PASS;
                    }
                    var pos = hit.getBlockPos();
                    var state = level.getBlockState(pos);
                    if (!player.isSpectator()) {
                        if (!StageGateEnforcer.allowInteractBlock(player, level, pos, state)
                                || !StageGateEnforcer.allowUseItem(player, player.getItemInHand(hand))) {
                            return InteractionResult.FAIL;
                        }
                    }
                    boolean cancelled = listener.apply(
                            new BlockRightClickEventJS(player, level, pos, state, hand),
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
                    if (!player.isSpectator()) {
                        if (!StageGateEnforcer.allowAttackWithItem(player, player.getItemInHand(hand))
                                || !StageGateEnforcer.allowInteractBlock(player, level, pos, state)
                                || !StageGateEnforcer.allowBreakBlock(player, level, pos, state)) {
                            return InteractionResult.FAIL;
                        }
                    }
                    boolean cancelled = listener.apply(
                            new BlockLeftClickEventJS(player, level, pos, state, hand), state.getBlock());
                    return cancelled ? InteractionResult.SUCCESS : InteractionResult.PASS;
                }));
    }
}
