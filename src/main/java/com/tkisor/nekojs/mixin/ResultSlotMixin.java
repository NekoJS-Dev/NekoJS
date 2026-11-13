//? if >=26 {
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.api.recipe.IngredientActionRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ingredient action consumption point. When the crafted result is taken, vanilla computes the
 * default remainder through {@code ResultSlot#getRemainingItems(CraftingInput, Level)}, which uses
 * {@code CraftingRecipe#defaultCraftingReminder} and returns the crafting remainder per item. After
 * it returns, the matched slots' remainder is transformed by the recipe id looked up in
 * {@link IngredientActionRegistry}.
 *
 * <p>The recipe id is resolved with the same query the server uses. The method already performed
 * that query internally but does not expose the id. Taking a crafted item is a player UI action, not
 * a hot path, so the second query is acceptable.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Inject(
            method = "getRemainingItems(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void nekojs$applyIngredientActions(CraftingInput input, Level level,
                                               CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        if (IngredientActionRegistry.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
                .ifPresent(holder -> {
                    // 26.x: RecipeHolder#id() is a ResourceKey<Recipe<?>>; the registry name is in its identifier().
                    Identifier recipeId = holder.id().identifier();
                    NonNullList<ItemStack> transformed =
                            IngredientActionRegistry.transform(recipeId, input, cir.getReturnValue());
                    cir.setReturnValue(transformed);
                });
    }
}
//?}
