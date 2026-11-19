package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class MixinArmorSlotStageGate {
    @Shadow
    @Final
    private LivingEntity owner;

    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true)
    private void nekojs$checkArmorPlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && owner instanceof ServerPlayer player
                && !StageGateEnforcer.allowEquipItem(player, stack)) {
            cir.setReturnValue(false);
        }
    }
}
