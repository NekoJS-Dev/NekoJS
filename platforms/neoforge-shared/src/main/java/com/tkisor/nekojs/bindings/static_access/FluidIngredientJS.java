package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class FluidIngredientJS {
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS of(Object... values) {
        com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS wrapper = new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS();
        if (values != null) {
            for (Object value : values) {
                wrapper.or(FluidResolver.ingredientFromValue(toValue(value)));
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

    public SizedFluidIngredient sized(Object value) {
        return FluidResolver.sizedFromValue(toValue(value));
    }

    public SizedFluidIngredient sized(Object value, int amount) {
        return FluidResolver.sizedFromIngredient(FluidResolver.ingredientFromValue(toValue(value)), amount);
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
