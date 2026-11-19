package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook enforcing declarative stage effect gates before a {@link MobEffectInstance} is
 * applied to a {@link LivingEntity}.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityEffectGate {

    @Inject(
            method = "canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nekojs$enforceEffectStageGate(
            MobEffectInstance effectInstance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (effectInstance == null || (self.level() != null && self.level().isClientSide())) {
            return;
        }
        if (!StageGateEnforcer.allowApplyEffect(self, effectInstance.getEffect())) {
            cir.setReturnValue(false);
        }
    }
}
