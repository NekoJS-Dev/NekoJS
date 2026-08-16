package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.12.2 FluidJS binding.
 * Uses FluidRegistry for fluid lookup and direct FluidStack construction.
 */
public class FluidJS {

    private static final int BUCKET = Fluid.BUCKET_VOLUME;

    /** Creates a fluid stack of one bucket (1000 mB) from a fluid-like value. */
    @Doc("Creates a fluid stack of one bucket (1000 mB) from a fluid-like value.")
    @Param(name = "value", value = "fluid id string like 'water', a FluidStack, a Fluid, or an object {fluid, amount}")
    @Return("the fluid stack, or null if the value is null; throws if the fluid id is unknown")
    public FluidStack of(Object value) {
        return resolveStack(toValue(value), BUCKET);
    }

    /** Creates a fluid stack with an explicit amount from a fluid-like value. */
    @Doc("Creates a fluid stack with an explicit amount from a fluid-like value.")
    @Param(name = "value", value = "fluid id string like 'water', a FluidStack, a Fluid, or an object {fluid, amount}")
    @Param(name = "amount", value = "fluid amount in mB, must be positive")
    @Return("a copy of the resolved stack with the given amount, or null if the value is null")
    public FluidStack of(Object value, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive: " + amount);
        FluidStack base = resolveStack(toValue(value), amount);
        if (base == null) return null;
        FluidStack copy = base.copy();
        copy.amount = amount;
        return copy;
    }

    /** Returns a water stack of one bucket (1000 mB). */
    @Doc("Returns a water stack of one bucket (1000 mB).")
    @Return("the water stack, or null if the water fluid is not registered")
    public FluidStack water() {
        return water(BUCKET);
    }

    /** Returns a water stack with the given amount. */
    @Doc("Returns a water stack with the given amount.")
    @Param(name = "amount", value = "fluid amount in mB")
    @Return("the water stack, or null if the water fluid is not registered")
    public FluidStack water(int amount) {
        Fluid fluid = FluidRegistry.getFluid("water");
        return fluid != null ? new FluidStack(fluid, amount) : null;
    }

    /** Returns a lava stack of one bucket (1000 mB). */
    @Doc("Returns a lava stack of one bucket (1000 mB).")
    @Return("the lava stack, or null if the lava fluid is not registered")
    public FluidStack lava() {
        return lava(BUCKET);
    }

    /** Returns a lava stack with the given amount. */
    @Doc("Returns a lava stack with the given amount.")
    @Param(name = "amount", value = "fluid amount in mB")
    @Return("the lava stack, or null if the lava fluid is not registered")
    public FluidStack lava(int amount) {
        Fluid fluid = FluidRegistry.getFluid("lava");
        return fluid != null ? new FluidStack(fluid, amount) : null;
    }

    /** 1.12.2 has no empty FluidStack; returns null. */
    @Doc("1.12.2 has no empty FluidStack representation.")
    @Return("always null")
    public FluidStack empty() {
        return null;
    }

    private FluidStack resolveStack(Value value, int fallbackAmount) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            String id = value.asString();
            Fluid fluid = FluidRegistry.getFluid(id);
            if (fluid == null) {
                // Try with minecraft: prefix
                fluid = FluidRegistry.getFluid("minecraft:" + id);
            }
            if (fluid == null) {
                throw new IllegalArgumentException("Fluid not found: " + id);
            }
            return new FluidStack(fluid, fallbackAmount);
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack) return stack;
            if (obj instanceof Fluid fluid) return new FluidStack(fluid, fallbackAmount);
        }

        if (value.hasMembers()) {
            String fluidId = null;
            int amount = fallbackAmount;
            if (value.hasMember("fluid")) fluidId = value.getMember("fluid").asString();
            else if (value.hasMember("id")) fluidId = value.getMember("id").asString();
            if (value.hasMember("amount")) amount = value.getMember("amount").asInt();
            if (fluidId != null) {
                Fluid fluid = FluidRegistry.getFluid(fluidId);
                if (fluid == null) throw new IllegalArgumentException("Fluid not found: " + fluidId);
                return new FluidStack(fluid, amount > 0 ? amount : fallbackAmount);
            }
        }

        throw new IllegalArgumentException("Cannot resolve FluidStack from: " + value);
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
