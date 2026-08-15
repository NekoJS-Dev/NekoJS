package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class FluidJS {
    public FluidStack of(Object value) {
        return FluidResolver.stackFromValue(toValue(value));
    }

    public FluidStack of(Object value, int amount) {
        FluidStack stack = of(value);
        if (stack.isEmpty()) return FluidStack.EMPTY;
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive: " + amount);
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    public FluidStack water() {
        return water(FluidResolver.stackFromFluid(Fluids.WATER).getAmount());
    }

    public FluidStack water(int amount) {
        return FluidResolver.stackFromFluid(Fluids.WATER, amount);
    }

    public FluidStack lava() {
        return lava(FluidResolver.stackFromFluid(Fluids.LAVA).getAmount());
    }

    public FluidStack lava(int amount) {
        return FluidResolver.stackFromFluid(Fluids.LAVA, amount);
    }

    public FluidStack empty() {
        return FluidStack.EMPTY;
    }

    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS ingredient(Object... values) {
        com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS wrapper = new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS();
        if (values != null) {
            for (Object value : values) {
                wrapper.or(FluidResolver.ingredientFromValue(toValue(value)));
            }
        }
        return wrapper;
    }

    public SizedFluidIngredient sizedIngredient(Object value) {
        return FluidResolver.sizedFromValue(toValue(value));
    }

    public SizedFluidIngredient sizedIngredient(Object value, int amount) {
        return FluidResolver.sizedFromIngredient(FluidResolver.ingredientFromValue(toValue(value)), amount);
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
