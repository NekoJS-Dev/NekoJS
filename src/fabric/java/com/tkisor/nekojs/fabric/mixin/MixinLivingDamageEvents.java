package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LivingEntity.class, Player.class})
public abstract class MixinLivingDamageEvents {
    @ModifyExpressionValue(method = "actuallyHurt", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F")
    })
    private float nekojs$beforeDamage(float amount, ServerLevel level, DamageSource source, float incoming) {
        return FabricEntityEventBindings.filterDamage((LivingEntity) (Object) this, source, amount);
    }

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0))
    private float nekojs$captureDamage(float amount, ServerLevel level, DamageSource source, float incoming) {
        FabricEntityEventBindings.captureDamage((LivingEntity) (Object) this, source, amount);
        return amount;
    }
}
