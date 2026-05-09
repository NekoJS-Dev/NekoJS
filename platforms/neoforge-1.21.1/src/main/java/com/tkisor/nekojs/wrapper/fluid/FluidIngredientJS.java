package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

public class FluidIngredientJS implements NekoWrapper<FluidIngredient> {
    private final List<FluidIngredient> alternatives = new ArrayList<>();

    public FluidIngredientJS() {}

    public FluidIngredientJS(String... ids) {
        for (String id : ids) or(id);
    }

    public FluidIngredientJS(FluidIngredient ingredient) {
        or(ingredient);
    }

    public FluidIngredientJS or(String id) {
        alternatives.add(FluidResolver.ingredientFromString(id));
        return this;
    }

    public FluidIngredientJS or(NekoId id) {
        alternatives.add(FluidResolver.ingredientFromString(id.toString()));
        return this;
    }

    public FluidIngredientJS or(Fluid fluid) {
        alternatives.add(FluidResolver.ingredientFromFluid(fluid));
        return this;
    }

    public FluidIngredientJS or(FluidStack stack) {
        alternatives.add(FluidResolver.ingredientFromStack(stack));
        return this;
    }

    public FluidIngredientJS or(FluidIngredient ingredient) {
        alternatives.add(ingredient);
        return this;
    }

    public FluidIngredientJS or(FluidIngredientJS other) {
        alternatives.add(other.unwrap());
        return this;
    }

    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    public SizedFluidIngredient withAmount(int amount) {
        return FluidResolver.sizedFromIngredient(unwrap(), amount);
    }

    public SizedFluidIngredient sized(int amount) {
        return withAmount(amount);
    }

    @Override
    public FluidIngredient unwrap() {
        return FluidResolver.combine(alternatives);
    }
}
