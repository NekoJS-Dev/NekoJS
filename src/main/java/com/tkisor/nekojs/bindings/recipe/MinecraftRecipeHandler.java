package com.tkisor.nekojs.bindings.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.graalvm.polyglot.Value;

public class MinecraftRecipeHandler {
    private final RecipeEventJS event;

    public MinecraftRecipeHandler(RecipeEventJS event) {
        this.event = event;
    }


    public void smelting(Ingredient input, ItemStack output, float xp, int cookTime) {
        event.builder("minecraft:smelting")
                .input("ingredient", input)
                .output("result", output)
                .property("experience", xp)
                .property("cookingtime", cookTime)
                .register();
    }

    public void shaped(ItemStack result, Value pattern, Value keys) {
        JsonArray patternArray = new JsonArray();
        for (int i = 0; i < pattern.getArraySize(); i++) {
            patternArray.add(pattern.getArrayElement(i).asString());
        }

        JsonObject keyObj = new JsonObject();
        for (String key : keys.getMemberKeys()) {
            Ingredient ing = keys.getMember(key).as(Ingredient.class);
            keyObj.add(key, event.serializeIngredient(ing));
        }

        event.builder("minecraft:crafting_shaped")
                .property("pattern", patternArray)
                .property("key", keyObj)
                .output("result", result)
                .register();
    }

    public void shapeless(ItemStack result, Value ingredients) {
        JsonArray ingredientsArray = new JsonArray();
        for (int i = 0; i < ingredients.getArraySize(); i++) {
            Ingredient ing = ingredients.getArrayElement(i).as(Ingredient.class);
            ingredientsArray.add(event.serializeIngredient(ing));
        }

        event.builder("minecraft:crafting_shapeless")
                .property("ingredients", ingredientsArray)
                .output("result", result)
                .register();
    }
}