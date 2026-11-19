package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.NeoForgeSupplementalFluidPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.FluidInteraction;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = FluidInteractionRegistry.class, remap = false)
public abstract class NeoForgeFluidInteractionMixin {
    @Unique
    private static InteractionInformation nekojs$firstDefaultInteraction;
    @Unique
    private static InteractionInformation nekojs$secondDefaultInteraction;

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry;addInteraction"
                    + "(Lnet/neoforged/neoforge/fluids/FluidType;"
                    + "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry$InteractionInformation;)V"),
            index = 1, require = 2, allow = 2)
    private static InteractionInformation nekojs$captureDefaultInteraction(InteractionInformation interaction) {
        if (nekojs$firstDefaultInteraction == null) {
            nekojs$firstDefaultInteraction = interaction;
        } else {
            nekojs$secondDefaultInteraction = interaction;
        }
        return interaction;
    }

    @WrapOperation(method = "canInteract(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry$FluidInteraction;interact"
                            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V"),
            require = 1, allow = 1)
    private static void nekojs$wrapDefaultInteraction(FluidInteraction callback, Level level, BlockPos pos,
                                                      BlockPos relativePos, FluidState state, Operation<Void> original,
                                                      @Local InteractionInformation interaction) {
        if (interaction == nekojs$firstDefaultInteraction || interaction == nekojs$secondDefaultInteraction) {
            NeoForgeSupplementalFluidPlacement.withVanillaHardening(level, pos,
                    () -> original.call(callback, level, pos, relativePos, state));
        } else {
            original.call(callback, level, pos, relativePos, state);
        }
    }
}
