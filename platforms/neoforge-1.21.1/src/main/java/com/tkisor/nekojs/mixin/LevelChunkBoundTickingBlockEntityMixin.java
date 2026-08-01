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
 * 方块实体 tick 注入：在 {@code LevelChunk$BoundTickingBlockEntity.tick} HEAD 触发
 * {@link BlockEntityTickEvent}（脚本侧 {@code BlockEvents.blockEntityTick}）。
 *
 * <p>对所有有 ticker 的方块实体触发（原版 + 脚本）；事件按 {@code BlockEntityType} 分发。
 * 目标内部类在两版本（1.21.1 / 26.x）均为 {@code net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity}。
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
