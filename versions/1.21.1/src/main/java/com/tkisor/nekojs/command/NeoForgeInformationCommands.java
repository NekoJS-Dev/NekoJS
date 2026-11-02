package com.tkisor.nekojs.command;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Read-only fluid-container inspection using the loader's capability API. */
final class NeoForgeInformationCommands {
    private NeoForgeInformationCommands() {}

    static Fluid containedFluid(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).map(FluidStack::getFluid).orElse(null);
    }
}
