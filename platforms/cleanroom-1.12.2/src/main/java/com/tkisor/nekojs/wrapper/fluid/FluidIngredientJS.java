package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Script-facing fluid ingredient wrapping a list of FluidStacks with OR semantics.")
@Doc("1.12.2 has no FluidIngredient class, so List<FluidStack> is the underlying representation.")
public class FluidIngredientJS implements NekoWrapper<List<FluidStack>> {
    private final List<FluidStack> alternatives = new ArrayList<>();
    private int amount = 1000;

    /** Creates an empty ingredient (matches nothing until an alternative is added). */
    public FluidIngredientJS() {}

    /** Creates an ingredient from fluid id strings. */
    @Doc("Creates an ingredient from one or more fluid id strings.")
    @Param(name = "ids", value = "fluid ids like 'water'; each becomes an alternative")
    public FluidIngredientJS(String... ids) {
        // Inline via a static helper instead of delegating to or(): calling an instance
        // method from a constructor can hand a partially initialized `this` out (this-escape).
        for (String id : ids) {
            addResolved(alternatives, FluidResolver.ingredientFromString(id));
        }
    }

    /** Wrap an existing list of FluidStacks. */
    @Doc("Creates an ingredient wrapping existing fluid stacks.")
    @Param(name = "stacks", value = "the stacks to wrap; copies are taken and the last amount becomes the default")
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

    /** Adds a fluid id alternative. */
    @Doc("Adds a fluid id as an alternative (OR).")
    @Param(name = "id", value = "fluid id like 'water'")
    @Return("this ingredient, for chaining")
    public FluidIngredientJS or(String id) {
        addResolved(alternatives, FluidResolver.ingredientFromString(id));
        return this;
    }

    private static void addResolved(List<FluidStack> target, List<FluidStack> resolved) {
        for (FluidStack fs : resolved) {
            if (fs != null && fs.amount > 0) {
                target.add(fs.copy());
            }
        }
    }

    /** Adds a NekoId alternative. */
    @Doc("Adds a NekoId as an alternative (OR).")
    @Param(name = "id", value = "the NekoId whose string form names a fluid")
    @Return("this ingredient, for chaining")
    public FluidIngredientJS or(NekoId id) {
        return or(id.toString());
    }

    /** Adds a fluid alternative. */
    @Doc("Adds a Fluid as an alternative (OR).")
    @Param(name = "fluid", value = "the fluid to add")
    @Return("this ingredient, for chaining")
    public FluidIngredientJS or(Fluid fluid) {
        List<FluidStack> resolved = FluidResolver.ingredientFromFluid(fluid);
        for (FluidStack fs : resolved) {
            if (fs != null && fs.amount > 0) {
                alternatives.add(fs.copy());
            }
        }
        return this;
    }

    /** Adds a fluid stack alternative. */
    @Doc("Adds a FluidStack as an alternative and adopts its amount as the default.")
    @Param(name = "stack", value = "the stack to add; a copy is taken")
    @Return("this ingredient, for chaining")
    public FluidIngredientJS or(FluidStack stack) {
        if (stack != null && stack.amount > 0) {
            alternatives.add(stack.copy());
            this.amount = stack.amount;
        }
        return this;
    }

    /** Adds a list of fluid stack alternatives. */
    @Doc("Adds every stack of a list as alternatives (OR).")
    @Param(name = "stacks", value = "the stacks to add; copies are taken")
    @Return("this ingredient, for chaining")
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

    /** OR-combines another FluidIngredientJS. */
    @Doc("Adds all alternatives of another FluidIngredientJS (OR).")
    @Param(name = "other", value = "the other fluid ingredient")
    @Return("this ingredient, for chaining")
    public FluidIngredientJS or(FluidIngredientJS other) {
        return or(other.getFluids());
    }

    // ===================== Accessors =====================

    /** Get the underlying fluid stacks. */
    @Doc("Gets the underlying fluid stacks.")
    @Return("an unmodifiable list of the alternative FluidStacks")
    public List<FluidStack> getFluids() {
        return Collections.unmodifiableList(alternatives);
    }

    /** Get the default amount. */
    @Doc("Gets the default amount in mB.")
    @Return("the default amount, initially 1000 (one bucket)")
    public int getAmount() {
        return amount;
    }

    /** Check if this ingredient is empty. */
    @Doc("Checks whether the ingredient has no alternatives.")
    @Return("true if nothing has been added yet")
    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    /** Check if a FluidStack matches this ingredient. */
    @Doc("Checks whether a fluid stack's fluid matches any alternative.")
    @Param(name = "stack", value = "the stack to test; only the fluid matters, not the amount")
    @Return("true if the fluid matches")
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
    @Doc("Copies all alternatives with a new amount.")
    @Param(name = "amount", value = "the amount in mB applied to every copy")
    @Return("a new list of FluidStack copies with the given amount")
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
    @Doc("Creates an ingredient from a single fluid id.")
    @Param(name = "fluidId", value = "fluid id like 'water'")
    @Return("a new FluidIngredientJS")
    public static FluidIngredientJS of(String fluidId) {
        return new FluidIngredientJS(FluidResolver.ingredientFromString(fluidId));
    }

    /** Create an ingredient from a fluid id with the given amount. */
    @Doc("Creates an ingredient from a fluid id with an explicit amount.")
    @Param(name = "fluidId", value = "fluid id like 'water'")
    @Param(name = "amount", value = "the amount in mB for every stack")
    @Return("a new FluidIngredientJS")
    public static FluidIngredientJS of(String fluidId, int amount) {
        List<FluidStack> stacks = FluidResolver.ingredientFromString(fluidId);
        for (FluidStack fs : stacks) {
            fs.amount = amount;
        }
        return new FluidIngredientJS(stacks);
    }

    /** Unwraps into a mutable copy of the alternatives. */
    @Doc("Unwraps the alternatives into a new mutable list.")
    @Return("a new list containing the alternative FluidStacks")
    @Override
    public List<FluidStack> unwrap() {
        return new ArrayList<>(alternatives);
    }
}
