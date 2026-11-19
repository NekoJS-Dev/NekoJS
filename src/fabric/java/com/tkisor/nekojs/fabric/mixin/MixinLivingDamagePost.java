package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MixinLivingDamagePost {
    @WrapMethod(method = "hurtServer")
    private boolean nekojs$postDamage(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return FabricEntityEventBindings.withDamagePost((LivingEntity) (Object) this, source,
                () -> original.call(level, source, amount));
    }

    @WrapOperation(method = "doHurtEquipment(Lnet/minecraft/world/damagesource/DamageSource;F[Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
            require = 1, allow = 1)
    private void nekojs$hurtEquipment(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot,
                                     Operation<Void> original) {
        if (entity instanceof ServerPlayer player) {
            FabricPlayerEventBindings.withEquipmentDamage(player, stack, () -> original.call(stack, amount, entity, slot));
        } else {
            original.call(stack, amount, entity, slot);
        }
    }
}
