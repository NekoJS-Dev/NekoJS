package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class FluidIngredientJS {
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS of(Value... values) {
        com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS wrapper = new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS();
        if (values != null) {
            for (Value value : values) {
                wrapper.or(FluidResolver.ingredientFromValue(value));
            }
        }
        return wrapper;
    }

    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS fluid(String id) {
        return new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS(id);
    }

    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS tag(String id) {
        return new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS(id.startsWith("#") ? id : "#" + id);
    }

    public SizedFluidIngredient sized(Value value) {
        return FluidResolver.sizedFromValue(value);
    }

    public SizedFluidIngredient sized(Value value, int amount) {
        return FluidResolver.sizedFromIngredient(FluidResolver.ingredientFromValue(value), amount);
    }
}
