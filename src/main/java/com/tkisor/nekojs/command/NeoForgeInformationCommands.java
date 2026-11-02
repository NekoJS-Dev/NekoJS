//? if neoforge {
package com.tkisor.nekojs.command;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/** Read-only fluid-container inspection using the loader's capability API. */
final class NeoForgeInformationCommands {
    private NeoForgeInformationCommands() {}

    static Fluid containedFluid(ItemStack stack) {
        var fluid = FluidUtil.getFirstStackContained(stack);
        return fluid.isEmpty() ? null : fluid.getFluid();
    }
}
//?}
