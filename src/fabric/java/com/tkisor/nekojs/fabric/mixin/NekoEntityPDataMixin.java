package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.NekoEntityPData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists the full {@code NeoForgeData} container on Fabric entities.
 *
 * <p>The duck root matches NeoForge's {@code Entity#getPersistentData()} shape; the platform
 * bridge selects {@link EntityExtension#NEKO_PDATA_KEY} once. Saving the container without
 * wrapping or flattening preserves unrelated keys and historical nested user data.
 */
@Mixin(Entity.class)
public abstract class NekoEntityPDataMixin implements NekoEntityPData {

    @Unique
    private static final String NEKO$CONTAINER_KEY = "NeoForgeData";

    @Unique
    private CompoundTag neko$pdataTag;

    @Unique
    private CompoundTag neko$ensureTag() {
        if (neko$pdataTag == null) {
            neko$pdataTag = new CompoundTag();
        }
        return neko$pdataTag;
    }

    /** Returns the mutable persistence container, not the NekoJS payload sub-key. */
    @Override
    public CompoundTag neko$getPDataRoot() {
        return neko$ensureTag();
    }

    // 注入点：saveWithoutId/load 里对 addAdditionalSaveData/readAdditionalSaveData 的调用点，
    // 不是那两个抽象方法本身——它们在 Entity 上无方法体，TAIL 注入永远找不到 RETURN（26.1.2
    // 实测崩溃）。挂调用点也与 NeoForge 补丁的位置一致：storeNullable("NeoForgeData") 就写在
    // addAdditionalSaveData 调用的紧前方。defaultRequire=1：MC 重构掉调用点时硬失败，不静默丢钩子。
    @Inject(method = "saveWithoutId", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
    private void neko$savePData(ValueOutput output, CallbackInfo ci) {
        if (neko$pdataTag != null && !neko$pdataTag.isEmpty()) {
            output.store(NEKO$CONTAINER_KEY, CompoundTag.CODEC, neko$pdataTag.copy());
        }
    }

    @Inject(method = "load", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void neko$loadPData(ValueInput input, CallbackInfo ci) {
        neko$pdataTag = input.read(NEKO$CONTAINER_KEY, CompoundTag.CODEC)
                .map(CompoundTag::copy)
                .orElse(null);
    }
}
