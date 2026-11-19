package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class MixinEntityRideTick {
    @WrapOperation(method = "rideTick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void nekojs$tickRider(Entity entity, Operation<Void> original) {
        FabricEntityEventBindingsV2.wrapStablePassengerTick(entity, original);
    }
}
