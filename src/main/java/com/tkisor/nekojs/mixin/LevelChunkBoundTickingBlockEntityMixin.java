//? if neoforge {
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the block entity tick: fires {@link BlockEntityTickEvent} (script side
 * {@code BlockEvents.blockEntityTick}) at the HEAD of {@code LevelChunk$BoundTickingBlockEntity.tick}.
 *
 * <p>Fires for every block entity that has a ticker, vanilla or scripted; the event dispatches by
 * {@code BlockEntityType}. The target inner class is
 * {@code net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity} in both versions
 * (1.21.1 and 26.x).
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class LevelChunkBoundTickingBlockEntityMixin {

    @Shadow
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void nekojs$fireBlockEntityTick(CallbackInfo ci) {
        if (!BlockEvents.BLOCK_ENTITY_TICK.canDispatch()) {
            return;
        }
        if (this.blockEntity == null || this.blockEntity.isRemoved() || !this.blockEntity.hasLevel()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new BlockEntityTickEvent(this.blockEntity));
    }
}
//?}
