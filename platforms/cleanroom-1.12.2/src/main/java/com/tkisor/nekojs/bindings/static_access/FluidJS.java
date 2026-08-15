package com.tkisor.nekojs.bindings.static_access;

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

    public FluidStack of(Object value) {
        return resolveStack(toValue(value), BUCKET);
    }

    public FluidStack of(Object value, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive: " + amount);
        FluidStack base = resolveStack(toValue(value), amount);
        if (base == null) return null;
        FluidStack copy = base.copy();
        copy.amount = amount;
        return copy;
    }

    public FluidStack water() {
        return water(BUCKET);
    }

    public FluidStack water(int amount) {
        Fluid fluid = FluidRegistry.getFluid("water");
        return fluid != null ? new FluidStack(fluid, amount) : null;
    }

    public FluidStack lava() {
        return lava(BUCKET);
    }

    public FluidStack lava(int amount) {
        Fluid fluid = FluidRegistry.getFluid("lava");
        return fluid != null ? new FluidStack(fluid, amount) : null;
    }

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
