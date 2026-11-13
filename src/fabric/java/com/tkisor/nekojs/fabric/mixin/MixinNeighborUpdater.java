package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code BlockEvents.neighborNotify}: the HEAD of
 * {@code NeighborUpdater#executeUpdate}, a <b>static</b> interface method.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>26.x moved the whole 1.21.x {@code Level.neighborChanged/updateNeighborsAt} network into
 *       {@code net.minecraft.world.level.redstone}. The methods of the same name on {@code Level}
 *       are now empty shells (updateNeighborsAt, neighborChanged and neighborShapeChanged all have
 *       a body of {@code return}), {@code ServerLevel} forwards to
 *       {@code CollectingNeighborUpdater} (see {@code ServerLevel.neighborChanged}, whose body is
 *       {@code neighborUpdater.neighborChanged(...)}), and the client equivalent is
 *       {@code InstantNeighborUpdater}.</li>
 *   <li>{@code NeighborUpdater.executeUpdate(Level, BlockState, BlockPos, Block, Orientation,
 *       boolean)} has a body: it calls
 *       {@code state.handleNeighborChanged(level, pos, block, orientation, forceRedstone)} inside
 *       a try-catch that builds a crash report ("Block being updated" / "Source block type"). So
 *       {@code pos/state} identify the neighbouring block being notified and {@code block} is the
 *       source block that changed, matching pos/state on NeoForge NeighborNotifyEvent (which has
 *       no 26.x counterpart for {@code getNotifiedSides()}, so the payload carries orientation
 *       instead).</li>
 *   <li>Both queue consumers ({@code CollectingNeighborUpdater$SimpleNeighborUpdate} for a single
 *       neighbour and {@code $FullNeighborUpdate} for updateNeighborsAt) converge on
 *       {@code executeUpdate} in their {@code runNext(Level)} (javap), making it the single sink
 *       for client and server and for single and full neighbour updates. The binding filters on
 *       {@code isClientSide}, so the SERVER bus only receives server-side instances.</li>
 * </ul>
 *
 * <p><b>Frequency</b>: every block change notification, including high-frequency redstone
 * circuits, reaches this hook. The injected code short-circuits on
 * {@code isClientSide} and {@code hasListeners} first, so no event object is created without
 * listeners. This is still a high-frequency path: keep script listeners light.
 */
@Mixin(NeighborUpdater.class)
public interface MixinNeighborUpdater {

    @Inject(method = "executeUpdate", at = @At("HEAD"), cancellable = true)
    private static void nekojs$onNeighborNotify(Level level, BlockState state, BlockPos pos,
                                                Block block, Orientation orientation,
                                                boolean forceRedstone, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postNeighborNotify(
                level, state, pos, block, orientation, forceRedstone)) {
            ci.cancel();
        }
    }
}
