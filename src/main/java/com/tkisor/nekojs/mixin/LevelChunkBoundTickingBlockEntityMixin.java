//? if neoforge {
package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
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

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"),
            require = 1, allow = 1)
    private void nekojs$beforeActualTick(BlockEntityTicker<?> ticker, Level level, BlockPos pos, BlockState state,
                                         BlockEntity entity, Operation<Void> original) {
        if (level instanceof ServerLevel serverLevel) {
            var bus = BlockEvents.BLOCK_ENTITY_TICK;
            if (serverLevel.getServer().isSameThread() && bus.canDispatch()) {
                bus.post(new BlockEntityTickEvent.Actual(entity, serverLevel, pos), entity.getType());
            }
        }
        original.call(ticker, level, pos, state, entity);
    }
}
//?}
