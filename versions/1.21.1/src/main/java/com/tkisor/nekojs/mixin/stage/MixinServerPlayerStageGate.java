package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Consumer;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerStageGate {
    @Inject(
            method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/util/FriendlyByteBufUtil;writeCustomData(Ljava/util/function/Consumer;Lnet/minecraft/core/RegistryAccess;)[B",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void nekojs$checkContainerBeforeOpenPacket(
            MenuProvider provider,
            Consumer<RegistryFriendlyByteBuf> extraData,
            CallbackInfoReturnable<OptionalInt> cir,
            @Local(ordinal = 0) AbstractContainerMenu menu
    ) {
        if (!StageGateEnforcer.allowOpenContainer((ServerPlayer) (Object) this, menu)) {
            cir.setReturnValue(OptionalInt.empty());
        }
    }
}
