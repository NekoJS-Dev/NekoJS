package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tkisor.nekojs.bindings.event.NeoForgeDamagePostRouting;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class NeoForgeLegacyDamagePostMixin {
    @WrapMethod(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean nekojs$afterHurt(DamageSource source, float amount, Operation<Boolean> original) {
        return NeoForgeDamagePostRouting.afterHurt((LivingEntity) (Object) this, source,
                () -> original.call(source, amount));
    }
}
