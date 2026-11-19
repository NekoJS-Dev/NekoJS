package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Equipable.class)
public interface MixinEquippableStageGate {
    @Inject(
            method = "swapWithEquipmentSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 0),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void nekojs$checkEquipmentSwap(
            Item item,
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir,
            ItemStack stack
    ) {
        if (player instanceof ServerPlayer serverPlayer && !StageGateEnforcer.allowEquipItem(serverPlayer, stack)) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }
}
