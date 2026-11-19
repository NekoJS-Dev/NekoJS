package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntityJoin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void nekojs$beforeEntityAdmission(EntityAccess value, boolean fromDisk, CallbackInfoReturnable<Boolean> ci) {
        if (value instanceof Entity entity && !(entity instanceof ServerPlayer) && entity.level() instanceof ServerLevel level
                && FabricEntityEventBindings.postJoinLevelPre(entity, level)) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void nekojs$afterEntityAdmission(EntityAccess value, boolean fromDisk, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && value instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            FabricEntityEventBindings.onEntityJoinedLevel(entity, level);
        }
    }
}
