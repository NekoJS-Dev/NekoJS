package com.tkisor.nekojs.bindings.static_access;

import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 FluidIngredientJS binding.
 * Since 1.12.2 has no FluidIngredient class, this returns FluidStack lists.
 */
public class FluidIngredientJS {

    public List<FluidStack> of(Object... values) {
        List<FluidStack> result = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                List<FluidStack> resolved = resolveIngredient(toValue(value));
                if (resolved != null) result.addAll(resolved);
            }
        }
        return result;
    }

    public List<FluidStack> fluid(String id) {
        List<FluidStack> result = new ArrayList<>();
        net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid != null) {
            result.add(new FluidStack(fluid, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME));
        }
        return result;
    }

    public List<FluidStack> tag(String id) {
        // 1.12.2 doesn't have fluid tags - just return empty
        return new ArrayList<>();
    }

    private List<FluidStack> resolveIngredient(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            String id = value.asString();
            net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(id);
            if (fluid != null) {
                List<FluidStack> result = new ArrayList<>();
                result.add(new FluidStack(fluid, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME));
                return result;
            }
            return null;
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack) {
                List<FluidStack> result = new ArrayList<>();
                result.add(stack);
                return result;
            }
            if (obj instanceof net.minecraftforge.fluids.Fluid fluid) {
                List<FluidStack> result = new ArrayList<>();
                result.add(new FluidStack(fluid, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME));
                return result;
            }
            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<FluidStack> list = (List<FluidStack>) obj;
                return new ArrayList<>(list);
            }
        }

        if (value.hasMembers()) {
            if (value.hasMember("fluid")) {
                return fluid(value.getMember("fluid").asString());
            }
            if (value.hasMember("id")) {
                return fluid(value.getMember("id").asString());
            }
        }

        return null;
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
