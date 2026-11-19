package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerStageGate {
    // Check the created menu before either of NeoForge's open packet paths.
    @Inject(
//? if neoforge {
            method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
//?} else {
            /*method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
*///?}
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/MenuProvider;createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"
            ),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private void nekojs$checkContainerBeforeOpenPacket(
            CallbackInfoReturnable<OptionalInt> cir,
            @Local(ordinal = 0) AbstractContainerMenu menu
    ) {
        if (!StageGateEnforcer.allowOpenContainer((ServerPlayer) (Object) this, menu)) {
            cir.setReturnValue(OptionalInt.empty());
        }
    }
}
