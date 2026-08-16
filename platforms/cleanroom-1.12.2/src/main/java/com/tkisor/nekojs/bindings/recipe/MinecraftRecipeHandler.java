package com.tkisor.nekojs.bindings.recipe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.12.2 MinecraftRecipeHandler - vanilla recipe manipulation.
 * Works directly with IRecipe/CraftingManager, not the datapack JSON system.
 */
public class MinecraftRecipeHandler {
    private final RecipeEventJS event;

    /** Wraps the recipe event for vanilla recipe manipulation. */
    @Doc("Vanilla recipe manipulation facade for the recipes event.")
    @Param(name = "event", value = "the RecipeEventJS instance this handler reads and mutates")
    public MinecraftRecipeHandler(RecipeEventJS event) {
        this.event = event;
    }

    // ========== Query ==========

    /** Lists all recipe registry ids. */
    @Doc("Lists the registry ids of all crafting recipes.")
    @Return("list of recipe ids like 'minecraft:stick'; never null")
    public List<String> list() {
        return event.getRecipeIds();
    }

    /** Gets the display name of a recipe's output. */
    @Doc("Gets the display name of a recipe's output stack.")
    @Param(name = "recipeId", value = "recipe registry id like 'minecraft:stick'")
    @Return("the output's display name, or null if the recipe or its output is missing")
    public String get(String recipeId) {
        IRecipe recipe = event.getRecipe(recipeId);
        if (recipe != null) {
            ItemStack out = recipe.getRecipeOutput();
            return out.isEmpty() ? null : out.getDisplayName();
        }
        return null;
    }

    /** Checks whether a recipe id exists. */
    @Doc("Checks whether a crafting recipe with the given id exists.")
    @Param(name = "recipeId", value = "recipe registry id")
    @Return("true if the recipe exists")
    public boolean exists(String recipeId) {
        return event.exists(recipeId);
    }

    /**
     * Remove a recipe by its registry name.
     */
    @Doc("Removes a crafting recipe by its registry id.")
    @Param(name = "recipeId", value = "recipe registry id like 'minecraft:stick'")
    public void remove(String recipeId) {
        event.remove(recipeId);
    }

    // ========== Shaped Recipe ==========

    /**
     * Add a shaped recipe with a key-based pattern.
     * @param output the result ItemStack
     * @param pattern rows of pattern characters (e.g. ["AAA", "B B", "AAA"])
     * @param keys mapping of character → Ingredient
     */
    @Doc("Adds a shaped crafting recipe with a key-based pattern.")
    @Param(name = "output", value = "the result item stack")
    @Param(name = "pattern", value = "rows of pattern characters, e.g. ['AAA', 'B B', 'AAA']; space means empty")
    @Param(name = "keys", value = "map of pattern character to ingredient")
    public void shaped(ItemStack output, List<String> pattern, Map<String, Ingredient> keys) {
        if (output.isEmpty() || pattern.isEmpty()) {
            NekoJS.LOGGER.warn("shaped recipe: empty output or pattern");
            return;
        }

        int height = pattern.size();
        int width = 0;
        for (String row : pattern) {
            if (row.length() > width) width = row.length();
        }

        // Build the ingredient array from pattern + keys
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) {
            String row = pattern.get(y);
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == ' ') continue;
                Ingredient ing = keys.get(String.valueOf(c));
                if (ing != null) {
                    ingredients.set(x + y * width, ing);
                }
            }
        }

        ResourceLocation id = generateId("shaped");
        ShapedRecipes recipe = new ShapedRecipes("nekojs", width, height, ingredients, output);
        recipe.setRegistryName(id);
        ForgeRegistries.RECIPES.register(recipe);
        boolean inForgeRegistry = ForgeRegistries.RECIPES.containsKey(id);
        IRecipe retrieved = CraftingManager.REGISTRY.getObject(id);
        NekoJS.LOGGER.info("Added shaped recipe: {} | inForgeRegistry={} craftingManagerGet={} totalRecipes={}",
                id, inForgeRegistry, retrieved != null, CraftingManager.REGISTRY.getKeys().size());
    }

    // ========== Shapeless Recipe ==========

    /**
     * Add a shapeless recipe.
     */
    @Doc("Adds a shapeless crafting recipe.")
    @Param(name = "output", value = "the result item stack")
    @Param(name = "ingredients", value = "list of ingredients; order does not matter")
    public void shapeless(ItemStack output, List<Ingredient> ingredients) {
        if (output.isEmpty() || ingredients.isEmpty()) {
            NekoJS.LOGGER.warn("shapeless recipe: empty output or ingredients");
            return;
        }

        NonNullList<Ingredient> nonNullList = NonNullList.create();
        nonNullList.addAll(ingredients);

        ResourceLocation id = generateId("shapeless");
        ShapelessRecipes recipe = new ShapelessRecipes("nekojs", output, nonNullList);
        recipe.setRegistryName(id);
        ForgeRegistries.RECIPES.register(recipe);
        NekoJS.LOGGER.info("Added shapeless recipe: {}", id);
    }

    // ========== Furnace ==========

    /**
     * Add a furnace smelting recipe.
     */
    @Doc("Adds a furnace smelting recipe.")
    @Param(name = "input", value = "the input item stack to smelt")
    @Param(name = "output", value = "the smelting result item stack")
    @Param(name = "xp", value = "experience granted per smelted item")
    public void smelting(ItemStack input, ItemStack output, float xp) {
        if (input.isEmpty() || output.isEmpty()) {
            NekoJS.LOGGER.warn("smelting recipe: empty input or output");
            return;
        }
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipe(input, output, xp);
        NekoJS.LOGGER.info("Added furnace recipe: {} → {} (xp: {})",
                input.getDisplayName(), output.getDisplayName(), xp);
    }

    /**
     * Remove a furnace recipe by input.
     */
    @Doc("Removes a furnace smelting recipe by its input stack.")
    @Param(name = "input", value = "the input item stack whose smelting recipe should be removed")
    public void removeSmelting(ItemStack input) {
        if (input.isEmpty()) return;
        Map<ItemStack, ItemStack> smeltingList = net.minecraft.item.crafting.FurnaceRecipes.instance().getSmeltingList();
        smeltingList.keySet().removeIf(stack -> stack.isItemEqual(input));
        NekoJS.LOGGER.info("Removed furnace recipe for: {}", input.getDisplayName());
    }

    // ========== Helpers ==========

    private int counter;
    private ResourceLocation generateId(String prefix) {
        return new ResourceLocation("nekojs", prefix + "_" + (counter++));
    }
}
