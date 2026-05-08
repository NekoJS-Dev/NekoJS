package com.tkisor.nekojs.bindings.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftRecipeHandler {
    private final RecipeEventJS event;

    public MinecraftRecipeHandler(RecipeEventJS event) {
        this.event = event;
    }

    public RecipeJsonBuilder smelting(Ingredient input, ItemStack output, float xp, int cookTime) {
        return event.builder("minecraft:smelting")
                .input("ingredient", input)
                .output("result", output)
                .property("experience", xp)
                .property("cookingtime", cookTime);
    }

    public RecipeJsonBuilder shaped(ItemStack result, List<String> pattern, Map<String, Ingredient> keys) {
        JsonArray patternArray = new JsonArray();
        for (String row : pattern) {
            patternArray.add(row);
        }

        JsonObject keyObj = new JsonObject();
        for (Map.Entry<String, Ingredient> entry : keys.entrySet()) {
            keyObj.add(entry.getKey(), event.serializeIngredient(entry.getValue()));
        }

        return event.builder("minecraft:crafting_shaped")
                .property("pattern", patternArray)
                .property("key", keyObj)
                .output("result", result);
    }

    public RecipeJsonBuilder shaped(ItemStack result, List<List<Ingredient>> inlinePattern) {
        JsonArray patternArray = new JsonArray();
        JsonObject keyObj = new JsonObject();

        Map<String, Character> uniquenessMap = new HashMap<>();
        char nextChar = 'A';

        for (List<Ingredient> row : inlinePattern) {
            StringBuilder rowBuilder = new StringBuilder();

            for (Ingredient ing : row) {
                if (ing == null || ing.isEmpty()) {
                    rowBuilder.append(' ');
                    continue;
                }

                JsonElement serializedIng = event.serializeIngredient(ing);
                String hash = serializedIng.toString();

                Character c = uniquenessMap.get(hash);
                if (c == null) {
                    c = nextChar++;
                    uniquenessMap.put(hash, c);
                    keyObj.add(String.valueOf(c), serializedIng);
                }
                rowBuilder.append(c);
            }
            patternArray.add(rowBuilder.toString());
        }

        return event.builder("minecraft:crafting_shaped")
                .property("pattern", patternArray)
                .property("key", keyObj)
                .output("result", result);
    }

    public RecipeJsonBuilder shapeless(ItemStack result, List<Ingredient> ingredients) {
        JsonArray ingredientsArray = new JsonArray();
        for (Ingredient ing : ingredients) {
            if (ing != null && !ing.isEmpty()) {
                ingredientsArray.add(event.serializeIngredient(ing));
            }
        }

        return event.builder("minecraft:crafting_shapeless")
                .property("ingredients", ingredientsArray)
                .output("result", result);
    }
}