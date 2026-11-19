package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityItemToss {
    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private boolean nekojs$routeDropInsertion(Level level, Entity drop, Operation<Boolean> original,
                                              @Local(argsOnly = true) ItemStack stack) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (FabricItemEventBindingsV2.deferPlayerTossInsertion(self, stack, drop, () -> {
            if (!FabricEntityEventBindings.captureSpawnedDrop(self, level, drop)) original.call(level, drop);
        })) return true;
        return FabricEntityEventBindings.captureSpawnedDrop(self, level, drop) || original.call(level, drop);
    }
}
