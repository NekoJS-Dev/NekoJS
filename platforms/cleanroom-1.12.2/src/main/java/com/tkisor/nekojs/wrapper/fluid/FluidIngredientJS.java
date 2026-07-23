package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.12.2 FluidIngredientJS — wraps a list of FluidStacks as a fluid ingredient.
 *
 * <p>Adapted from neoforge-26.1 FluidIngredientJS. Since 1.12.2 has no
 * {@code FluidIngredient} class, this class uses {@code List<FluidStack>} as the
 * underlying representation.</p>
 */
public class FluidIngredientJS implements NekoWrapper<List<FluidStack>> {
    private final List<FluidStack> alternatives = new ArrayList<>();
    private int amount = 1000;

    public FluidIngredientJS() {}

    public FluidIngredientJS(String... ids) {
        for (String id : ids) or(id);
    }

    /** Wrap an existing list of FluidStacks. */
    public FluidIngredientJS(List<FluidStack> stacks) {
        if (stacks != null) {
            for (FluidStack fs : stacks) {
                if (fs != null && fs.amount > 0) {
                    this.alternatives.add(fs.copy());
                    this.amount = fs.amount;
                }
            }
        }
    }

    // ===================== Builder methods =====================

    public FluidIngredientJS or(String id) {
        List<FluidStack> resolved = FluidResolver.ingredientFromString(id);
        for (FluidStack fs : resolved) {
            if (fs != null && fs.amount > 0) {
                alternatives.add(fs.copy());
            }
        }
        return this;
    }

    public FluidIngredientJS or(NekoId id) {
        return or(id.toString());
    }

    public FluidIngredientJS or(Fluid fluid) {
        List<FluidStack> resolved = FluidResolver.ingredientFromFluid(fluid);
        for (FluidStack fs : resolved) {
            if (fs != null && fs.amount > 0) {
                alternatives.add(fs.copy());
            }
        }
        return this;
    }

    public FluidIngredientJS or(FluidStack stack) {
        if (stack != null && stack.amount > 0) {
            alternatives.add(stack.copy());
            this.amount = stack.amount;
        }
        return this;
    }

    public FluidIngredientJS or(List<FluidStack> stacks) {
        if (stacks != null) {
            for (FluidStack fs : stacks) {
                if (fs != null && fs.amount > 0) {
                    alternatives.add(fs.copy());
                }
            }
        }
        return this;
    }

    public FluidIngredientJS or(FluidIngredientJS other) {
        return or(other.getFluids());
    }

    // ===================== Accessors =====================

    /** Get the underlying fluid stacks. */
    public List<FluidStack> getFluids() {
        return Collections.unmodifiableList(alternatives);
    }

    /** Get the default amount. */
    public int getAmount() {
        return amount;
    }

    /** Check if this ingredient is empty. */
    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    /** Check if a FluidStack matches this ingredient. */
    public boolean matches(FluidStack stack) {
        if (stack == null) return false;
        for (FluidStack fs : alternatives) {
            if (fs.getFluid() == stack.getFluid()) return true;
        }
        return false;
    }

    /**
     * Create a sized copy with the given amount.
     * Returns a new list of stacks each with the specified amount.
     */
    public List<FluidStack> withAmount(int amount) {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack fs : alternatives) {
            FluidStack copy = fs.copy();
            copy.amount = amount;
            result.add(copy);
        }
        return result;
    }

    // ===================== Static factory methods =====================

    /** Create an ingredient from a single fluid id. */
    public static FluidIngredientJS of(String fluidId) {
        return new FluidIngredientJS(FluidResolver.ingredientFromString(fluidId));
    }

    /** Create an ingredient from a fluid id with the given amount. */
    public static FluidIngredientJS of(String fluidId, int amount) {
        List<FluidStack> stacks = FluidResolver.ingredientFromString(fluidId);
        for (FluidStack fs : stacks) {
            fs.amount = amount;
        }
        return new FluidIngredientJS(stacks);
    }

    @Override
    public List<FluidStack> unwrap() {
        return new ArrayList<>(alternatives);
    }
}
