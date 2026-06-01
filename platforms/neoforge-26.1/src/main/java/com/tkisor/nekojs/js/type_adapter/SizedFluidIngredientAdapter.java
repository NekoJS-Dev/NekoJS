package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public final class SizedFluidIngredientAdapter implements JSTypeAdapter<SizedFluidIngredient> {
    @Override
    public Class<SizedFluidIngredient> getTargetClass() {
        return SizedFluidIngredient.class;
    }

    @Override
    public boolean test(Value value) {
        if (value.isString() || value.hasMembers()) return true;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof SizedFluidIngredient || obj instanceof FluidIngredient || obj instanceof FluidIngredientJS || obj instanceof FluidStack || obj instanceof Fluid || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public SizedFluidIngredient apply(Value value) {
        return FluidResolver.sizedFromValue(value);
    }
}
