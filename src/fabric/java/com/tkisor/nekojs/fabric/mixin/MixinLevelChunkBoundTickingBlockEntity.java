// fabric 孪生：NeoForge 侧同位次实现见 src/main/java/com/tkisor/nekojs/mixin/LevelChunkBoundTickingBlockEntityMixin.java
package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块实体 tick 事件（{@code BlockEvents.blockEntityTick}）的 fabric 挂点：
 * {@code LevelChunk$BoundTickingBlockEntity#tick} HEAD——与 NeoForge 侧
 * {@code LevelChunkBoundTickingBlockEntityMixin} 同点孪生（javap 实证
 * minecraft-merged-deobf-26.1.2：内部类与本挂点在 1.21.1 / 26.x 均存在，
 * 泛型字段 {@code blockEntity} 擦除后为 {@code BlockEntity}）。
 *
 * <p>失效实体守卫（null/已移除/无 level）与 NF 侧一致。按
 * {@code BlockEntityType} 分发；不可取消；高频通道，无监听器时零事件对象。
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
