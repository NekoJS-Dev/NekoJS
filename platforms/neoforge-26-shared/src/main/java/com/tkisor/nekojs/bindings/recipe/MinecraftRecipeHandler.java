package com.tkisor.nekojs.bindings.recipe;

import com.google.gson.JsonArray;
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

    public MinecraftRecipeHandler(RecipeEventJS event) { this.event = event; }

    // ========== crafting_shaped ==========

    public RecipeJsonBuilder crafting_shaped(ItemStack result, List<String> pattern,
                                             Map<String, Ingredient> keys) {
        JsonArray patternArray = new JsonArray();
        for (String row : pattern) patternArray.add(row);
        JsonObject keyObj = new JsonObject();
        for (var entry : keys.entrySet())
            keyObj.add(entry.getKey(), event.serializeIngredient(entry.getValue()));
        RecipeJsonBuilder builder = event.builder("minecraft:crafting_shaped")
                .jsonProperty("pattern", patternArray).jsonProperty("key", keyObj).output("result", result);
        return builder;
    }

    public RecipeJsonBuilder crafting_shaped(ItemStack result, List<List<Ingredient>> inlinePattern) {
        JsonArray patternArray = new JsonArray();
        JsonObject keyObj = new JsonObject();
        Map<String, Character> seen = new HashMap<>();
        char next = 'A';
        for (var row : inlinePattern) {
            StringBuilder sb = new StringBuilder();
            for (Ingredient ing : row) {
                if (ing == null || ing.isEmpty()) { sb.append(' '); continue; }
                String hash = event.serializeIngredient(ing).toString();
                Character c = seen.get(hash);
                if (c == null) { c = next++; seen.put(hash, c); keyObj.add(String.valueOf(c), event.serializeIngredient(ing)); }
                sb.append(c);
            }
            patternArray.add(sb.toString());
        }
        RecipeJsonBuilder builder = event.builder("minecraft:crafting_shaped")
                .jsonProperty("pattern", patternArray).jsonProperty("key", keyObj).output("result", result);
        return builder;
    }

    public RecipeJsonBuilder crafting_shapeless(ItemStack result, List<Ingredient> ingredients) {
        JsonArray arr = new JsonArray();
        for (Ingredient ing : ingredients)
            if (ing != null && !ing.isEmpty()) arr.add(event.serializeIngredient(ing));
        RecipeJsonBuilder builder = event.builder("minecraft:crafting_shapeless")
                .jsonProperty("ingredients", arr).output("result", result);
        return builder;
    }
}
