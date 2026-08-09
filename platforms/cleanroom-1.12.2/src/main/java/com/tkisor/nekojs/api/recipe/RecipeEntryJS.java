package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 RecipeEntryJS - a script-facing view over a live {@link IRecipe}.
 *
 * <p>Unlike the datapack-era platforms, 1.12.2 recipes do not carry JSON at runtime,
 * so this entry exposes the recipe's structural fields (id / type / group / output /
 * ingredients) directly from the {@code IRecipe} object. JSON-mutation methods
 * ({@code setPath}/{@code merge}/{@code builder}) are intentionally absent because
 * there is no JSON tree to mutate on 1.12.2; use {@code event.recipes.minecraft.shaped/...}
 * to (re-)create recipes instead.
 */
public class RecipeEntryJS {
    private final RecipeEventJS event;
    private final IRecipe recipe;

    public RecipeEntryJS(RecipeEventJS event, IRecipe recipe) {
        this.event = event;
        this.recipe = recipe;
    }

    /** The recipe registry id as a string (e.g. {@code minecraft:iron_ingot_from_smelting_iron_ore}). */
    public String id() {
        ResourceLocation rl = recipe.getRegistryName();
        return rl == null ? "" : rl.toString();
    }

    /**
     * Best-effort recipe type id. 1.12.2 has no recipe-type registry keyed by id, so this
     * derives the type string from the concrete class name (shaped/shapeless/furnace/...).
     */
    public String type() {
        return RecipeEventJS.getRecipeTypeId(recipe);
    }

    public String group() {
        try {
            return recipe.getGroup();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** The result ItemStack (a copy; safe to inspect). Empty if the recipe has no output. */
    public ItemStack output() {
        ItemStack out = recipe.getRecipeOutput();
        return out == null ? ItemStack.EMPTY : out.copy();
    }

    /** Convenience: the registry id of the result item, or {@code null}. */
    public String outputId() {
        ItemStack out = recipe.getRecipeOutput();
        if (out == null || out.isEmpty()) return null;
        ResourceLocation rl = out.getItem().getRegistryName();
        return rl == null ? null : rl.toString();
    }

    /** The recipe's input ingredients (flattened, non-null). */
    public List<Ingredient> ingredients() {
        return RecipeEventJS.getIngredients(recipe);
    }

    /** The underlying IRecipe (for advanced/host access). */
    public IRecipe recipe() {
        return recipe;
    }

    public RecipeEventJS event() {
        return event;
    }

    /** Remove this recipe from the registry (delegates to {@link RecipeEventJS#removeById(String)}). */
    public void remove() {
        ResourceLocation rl = recipe.getRegistryName();
        if (rl != null) {
            event.removeById(rl.toString());
        }
    }

    @Override
    public String toString() {
        ItemStack out = recipe.getRecipeOutput();
        String outName = (out == null || out.isEmpty()) ? "<none>" : out.getDisplayName();
        return "RecipeEntryJS{id=" + id() + ", type=" + type() + ", output=" + outName + "}";
    }

    /** Helper for filters that want the matching-stacks view of every input ingredient. */
    public List<ItemStack> inputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Ingredient ing : ingredients()) {
            if (ing == null) continue;
            for (ItemStack s : ing.getMatchingStacks()) {
                if (s != null && !s.isEmpty()) stacks.add(s);
            }
        }
        return stacks;
    }
}
