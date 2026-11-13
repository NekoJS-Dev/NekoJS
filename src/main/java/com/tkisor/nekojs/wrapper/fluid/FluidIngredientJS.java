//? if neoforge {
package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluid ingredient wrapper: alternatives combine as OR, {@code unwrap()} collapses them into a single
 * {@link FluidIngredient}, and {@code withAmount} attaches an amount to get a {@link SizedFluidIngredient}.
 */
@Doc("A fluid ingredient built from OR-combined alternatives.")
@Doc("Use withAmount(n) to turn it into a sized fluid ingredient requiring n millibuckets.")
public class FluidIngredientJS implements NekoWrapper<FluidIngredient> {
    private final List<FluidIngredient> alternatives = new ArrayList<>();

    public FluidIngredientJS() {}

    /** Creates the ingredient from fluid ids (selectors such as {@code '#tag'} are supported). */
    @Doc("Creates a fluid ingredient matching any of the given fluid ids.")
    @Param(name = "ids", value = "fluid ids; '#tag', '@mod', '*', and '/regex/' selector strings also work")
    public FluidIngredientJS(String... ids) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        for (String id : ids) alternatives.add(FluidResolver.ingredientFromString(id));
    }

    /** Creates the ingredient from an existing ingredient. */
    @Doc("Creates a fluid ingredient from an existing FluidIngredient.")
    @Param(name = "ingredient", value = "the ingredient to wrap")
    public FluidIngredientJS(FluidIngredient ingredient) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        alternatives.add(ingredient);
    }

    /** Adds an alternative: a fluid id. */
    @Doc("Adds a fluid id as another accepted alternative.")
    @Param(name = "id", value = "fluid id or selector string like '#tag'")
    @Return("this, for chaining")
    public FluidIngredientJS or(String id) {
        alternatives.add(FluidResolver.ingredientFromString(id));
        return this;
    }

    /** Adds an alternative: a {@link NekoId}. */
    @Doc("Adds a NekoId as another accepted alternative.")
    @Param(name = "id", value = "fluid id as a NekoId")
    @Return("this, for chaining")
    public FluidIngredientJS or(NekoId id) {
        alternatives.add(FluidResolver.ingredientFromString(id.toString()));
        return this;
    }

    /** Adds an alternative: a fluid. */
    @Doc("Adds a fluid as another accepted alternative.")
    @Param(name = "fluid", value = "the fluid to accept")
    @Return("this, for chaining")
    public FluidIngredientJS or(Fluid fluid) {
        alternatives.add(FluidResolver.ingredientFromFluid(fluid));
        return this;
    }

    /** Adds an alternative: a fluid stack (matching its fluid). */
    @Doc("Adds the fluid of a stack as another accepted alternative.")
    @Param(name = "stack", value = "the fluid stack whose fluid is accepted")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidStack stack) {
        alternatives.add(FluidResolver.ingredientFromStack(stack));
        return this;
    }

    /** Adds an alternative: an existing ingredient. */
    @Doc("Adds an existing FluidIngredient as another accepted alternative.")
    @Param(name = "ingredient", value = "the ingredient to OR in")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidIngredient ingredient) {
        alternatives.add(ingredient);
        return this;
    }

    /** Adds an alternative: another wrapper object. */
    @Doc("Adds all alternatives of another FluidIngredientJS.")
    @Param(name = "other", value = "wrapper whose combined alternatives are OR-joined into this one")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidIngredientJS other) {
        alternatives.add(other.unwrap());
        return this;
    }

    /** Whether no alternative has been added yet (an empty ingredient). */
    @Doc("Checks whether no alternative has been added yet.")
    @Return("true when the wrapper holds no alternatives")
    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    /** Attaches an amount (mB) to get a sized fluid ingredient. */
    @Doc("Turns this ingredient into a sized one requiring the given amount.")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("a SizedFluidIngredient matching the same fluids with the given amount")
    public SizedFluidIngredient withAmount(int amount) {
        return FluidResolver.sizedFromIngredient(unwrap(), amount);
    }

    /** Short alias of {@link #withAmount(int)}. */
    @Doc("Alias of withAmount(amount).")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("a SizedFluidIngredient matching the same fluids with the given amount")
    public SizedFluidIngredient sized(int amount) {
        return withAmount(amount);
    }

    /** Unwraps: combines the alternatives into a single {@link FluidIngredient} (a single alternative is returned as-is). */
    @Doc("Unwraps to a single FluidIngredient; throws when no alternative has been added.")
    @Return("the single alternative as-is, or a combined OR ingredient when several were added")
    @Override
    public FluidIngredient unwrap() {
        return FluidResolver.combine(alternatives);
    }
}
//?}
