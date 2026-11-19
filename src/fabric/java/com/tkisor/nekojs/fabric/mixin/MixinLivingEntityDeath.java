package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDeath {

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;",
            ordinal = 0), cancellable = true)
    private void nekojs$onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.level().isClientSide() && FabricEntityEventBindings.postStableDeath(entity, source)) {
            ci.cancel();
        }
    }

    @Mixin(ServerPlayer.class)
    public abstract static class ServerPlayerDeath {

        @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"), cancellable = true)
        private void nekojs$onDeath(DamageSource source, CallbackInfo ci) {
            if (FabricEntityEventBindings.postStableDeath((ServerPlayer) (Object) this, source)) {
                ci.cancel();
            }
        }
    }
}
