package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Equippable.class)
public abstract class MixinEquippableStageGate {
    @Inject(
            method = "swapWithEquipmentSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;level()Lnet/minecraft/world/level/Level;"),
            cancellable = true
    )
    private void nekojs$checkEquipmentSwap(ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && !StageGateEnforcer.allowEquipItem(serverPlayer, stack)) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
