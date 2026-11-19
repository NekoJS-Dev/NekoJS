package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook enforcing declarative stage entity-mount gates on {@link Entity#startRiding(Entity)}
 * and {@code Entity#canRide(Entity)}.
 */
@Mixin(Entity.class)
public abstract class MixinEntityMountGate {

    @Inject(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nekojs$enforceStartRidingStageGate(
            Entity vehicle,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide()) {
            return;
        }
        if (!StageGateEnforcer.allowMountEntity(self, vehicle)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "canRide(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nekojs$enforceCanRideStageGate(
            Entity vehicle,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide()) {
            return;
        }
        if (!StageGateEnforcer.allowMountEntity(self, vehicle)) {
            cir.setReturnValue(false);
        }
    }
}
