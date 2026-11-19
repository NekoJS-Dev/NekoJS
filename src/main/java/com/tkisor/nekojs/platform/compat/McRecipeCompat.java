package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

public final class McRecipeCompat {
    private McRecipeCompat() {}

    public static ItemStack assemble(CraftingRecipe recipe, CraftingInput input, Level level) {
//? if >=26 {
        return recipe.assemble(input);
//?} else {
        /*return recipe.assemble(input, level.registryAccess());
*///?}
    }
}
