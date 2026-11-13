package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code BlockEvents.portalSpawn}: the HEAD of
 * {@code PortalShape#createPortalBlocks(LevelAccessor)}.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code public void createPortalBlocks(LevelAccessor)} is a concrete method whose body is
 *       exactly the portal block placement loop
 *       ({@code Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis)}
 *       over {@code BlockPos.betweenClosed}). Cancelling at HEAD places no portal blocks, which
 *       matches NeoForge PortalSpawnEvent (an ICancellableEvent).</li>
 *   <li>The constructor is package-private ({@code PortalShape(Axis, int, Direction, BlockPos,
 *       int, int)}, called only by a package static factory). The coordinates and axis are private
 *       and have no getters, so the mixin reads {@code axis} and {@code bottomLeft} with
 *       {@link Shadow} to build the payload; Shadow in a mixin needs no AW, but shared-tree code
 *       would need one to read these members directly.</li>
 *   <li>A full scan of the jar's constant pool finds only two references to
 *       {@code createPortalBlocks}: this class and {@code BaseFireBlock#onPlace} (the ignition
 *       path, after {@code findEmptyPortalShape(level, pos, Axis.X)} hits). That is one firing
 *       point with no duplicate delivery.</li>
 * </ul>
 *
 * <p>Not covered: {@code PortalForcer#createPortal}. When an entity crosses dimensions and no
 * portal exists, it builds the frame and the portal blocks programmatically with direct
 * {@code setBlock} calls and never goes through {@code PortalShape}, so this hook does not see it.
 */
@Mixin(PortalShape.class)
public abstract class MixinPortalShape {

    @Shadow
    private Direction.Axis axis;

    @Shadow
    private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onPortalSpawn(LevelAccessor level, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        // Identical to the placement state vanilla builds inside createPortalBlocks (checked with javap)
        BlockState state = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, this.axis);
        if (FabricBlockEventBindingsV2.postPortalSpawn(
                level, this.bottomLeft, state, (PortalShape) (Object) this)) {
            ci.cancel();
        }
    }
}
