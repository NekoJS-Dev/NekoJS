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

    // Injection point: the call to addAdditionalSaveData/readAdditionalSaveData inside
    // saveWithoutId/load, not those two abstract methods themselves. They have no body on Entity,
    // so a TAIL injection never finds a RETURN (this crashes on 26.1.2). Hooking the call site also
    // matches where the NeoForge patch sits: storeNullable("NeoForgeData") is written right before
    // the addAdditionalSaveData call. defaultRequire=1 makes a refactor that removes the call site
    // fail hard instead of dropping the hook silently.
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
