//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Script static binding {@code Fluid}: factory for fluid stacks and fluid ingredients.
 */
@Doc("Static binding 'Fluid': factory helpers for fluid stacks and fluid ingredients.")
public class FluidJS {
    /** Resolves a fluid stack from an id, a fluid object, or a {@code {fluid, amount}} object
     * (one bucket by default). */
    @Doc("Creates a fluid stack from a fluid-like value.")
    @Doc("Accepts a fluid id like 'minecraft:water', a Fluid/FluidStack object, or a JS object {fluid|id, amount}.")
    @Param(name = "value", value = "fluid id, fluid/fluid stack object, or {fluid|id, amount} JS object")
    @Return("the resolved FluidStack; empty when the value is null/empty (amount defaults to one bucket)")
    public FluidStack of(Object value) {
        return FluidResolver.stackFromValue(toValue(value));
    }

    /** Same as {@link #of(Object)}, but overrides the amount in mB; a non-positive amount throws. */
    @Doc("Creates a fluid stack with an explicit amount in millibuckets.")
    @Param(name = "value", value = "fluid id, fluid/fluid stack object, or {fluid|id, amount} JS object")
    @Param(name = "amount", value = "stack amount in millibuckets; must be positive")
    @Return("a copy of the resolved stack with the given amount, or empty when the fluid is empty")
    public FluidStack of(Object value, int amount) {
        FluidStack stack = of(value);
        if (stack.isEmpty()) return FluidStack.EMPTY;
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive: " + amount);
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    /** One bucket of water. */
    @Doc("Creates a water stack of one bucket.")
    @Return("a FluidStack of water, 1000 mB")
    public FluidStack water() {
        return water(FluidResolver.stackFromFluid(Fluids.WATER).getAmount());
    }

    /** Water of the given amount in mB. */
    @Doc("Creates a water stack with the given amount.")
    @Param(name = "amount", value = "stack amount in millibuckets")
    @Return("a FluidStack of water with the given amount")
    public FluidStack water(int amount) {
        return FluidResolver.stackFromFluid(Fluids.WATER, amount);
    }

    /** One bucket of lava. */
    @Doc("Creates a lava stack of one bucket.")
    @Return("a FluidStack of lava, 1000 mB")
    public FluidStack lava() {
        return lava(FluidResolver.stackFromFluid(Fluids.LAVA).getAmount());
    }

    /** Lava of the given amount in mB. */
    @Doc("Creates a lava stack with the given amount.")
    @Param(name = "amount", value = "stack amount in millibuckets")
    @Return("a FluidStack of lava with the given amount")
    public FluidStack lava(int amount) {
        return FluidResolver.stackFromFluid(Fluids.LAVA, amount);
    }

    /** An empty fluid stack. */
    @Doc("Creates an empty fluid stack.")
    @Return("the shared FluidStack.EMPTY instance")
    public FluidStack empty() {
        return FluidStack.EMPTY;
    }

    /** Builds a fluid ingredient that matches any of the given fluid values. */
    @Doc("Builds a fluid ingredient matching any of the given fluid-like values.")
    @Param(name = "values", value = "fluid ids ('#tag', '@mod', '*', '/regex/' also work), fluids, stacks, or ingredients")
    @Return("a FluidIngredientJS combining all values with OR; empty wrapper when no value is given")
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS ingredient(Object... values) {
        com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS wrapper = new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS();
        if (values != null) {
            for (Object value : values) {
                wrapper.or(FluidResolver.ingredientFromValue(toValue(value)));
            }
        }
        return wrapper;
    }

    /** Resolves a sized fluid ingredient from a fluid value; the value may carry its own amount. */
    @Doc("Creates a sized fluid ingredient from a fluid-like value carrying its own amount.")
    @Param(name = "value", value = "fluid id, stack, or {fluid|id, amount} JS object")
    @Return("the resolved SizedFluidIngredient")
    public SizedFluidIngredient sizedIngredient(Object value) {
        return FluidResolver.sizedFromValue(toValue(value));
    }

    /** Resolves a sized fluid ingredient from a fluid value and an explicit amount in mB. */
    @Doc("Creates a sized fluid ingredient with an explicit amount in millibuckets.")
    @Param(name = "value", value = "fluid id, stack, or ingredient-like value (its own amount is ignored)")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("the resolved SizedFluidIngredient with the given amount")
    public SizedFluidIngredient sizedIngredient(Object value, int amount) {
        return FluidResolver.sizedFromIngredient(FluidResolver.ingredientFromValue(toValue(value)), amount);
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
//?}
