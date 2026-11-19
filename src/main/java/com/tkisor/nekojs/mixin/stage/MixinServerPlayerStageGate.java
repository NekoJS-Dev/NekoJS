package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerStageGate {
    @Inject(
            method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;<init>(ILnet/minecraft/world/inventory/MenuType;Lnet/minecraft/network/chat/Component;)V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void nekojs$checkContainerBeforeOpenPacket(
            MenuProvider provider,
            CallbackInfoReturnable<OptionalInt> cir,
            @Local(ordinal = 0) AbstractContainerMenu menu
    ) {
        if (!StageGateEnforcer.allowOpenContainer((ServerPlayer) (Object) this, menu)) {
            cir.setReturnValue(OptionalInt.empty());
        }
    }
}
