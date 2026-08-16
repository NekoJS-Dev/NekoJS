package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** Collects fluid stacks from any number of fluid-like values. */
    @Doc("Collects fluid stacks from any number of fluid-like values.")
    @Param(name = "values", value = "fluid id strings, FluidStacks, Fluids, or lists thereof; unknown ids are skipped")
    @Return("a new list of resolved fluid stacks (one bucket each); never null, may be empty")
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

    /** Returns a single one-bucket stack for a fluid id. */
    @Doc("Returns a single one-bucket (1000 mB) stack for a fluid id.")
    @Param(name = "id", value = "fluid id like 'water'")
    @Return("a list holding the stack, or an empty list if the fluid is unknown")
    public List<FluidStack> fluid(String id) {
        List<FluidStack> result = new ArrayList<>();
        net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid != null) {
            result.add(new FluidStack(fluid, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME));
        }
        return result;
    }

    /** 1.12.2 has no fluid tags; always returns an empty list. */
    @Doc("Fluid tags do not exist on 1.12.2.")
    @Param(name = "id", value = "tag id; ignored on this platform")
    @Return("always an empty list")
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
