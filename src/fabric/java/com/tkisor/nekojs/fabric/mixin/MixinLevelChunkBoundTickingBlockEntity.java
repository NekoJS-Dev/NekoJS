// Fabric twin of the implementation at the same point on the NeoForge side, in
// src/main/java/com/tkisor/nekojs/mixin/LevelChunkBoundTickingBlockEntityMixin.java
package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for the block entity tick event ({@code BlockEvents.blockEntityTick}): the HEAD of
 * {@code LevelChunk$BoundTickingBlockEntity#tick}, the same point as
 * {@code LevelChunkBoundTickingBlockEntityMixin} on NeoForge (checked with javap on
 * minecraft-merged-deobf-26.1.2: the inner class and this hook exist in both 1.21.1 and 26.x, and
 * the generic field {@code blockEntity} erases to {@code BlockEntity}).
 *
 * <p>The invalid-entity guard (null, removed, or no level) matches the NeoForge side. Dispatch is
 * by {@code BlockEntityType}. Not cancellable; high-frequency path, so no listeners means no
 * event object.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class MixinLevelChunkBoundTickingBlockEntity {

    @Shadow
    private BlockEntity blockEntity;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nekojs$onBlockEntityTick(CallbackInfo ci) {
        if (this.blockEntity == null || this.blockEntity.isRemoved() || !this.blockEntity.hasLevel()) {
            return;
        }
        FabricBlockEventBindingsV2.postBlockEntityTick(this.blockEntity);
    }
}
