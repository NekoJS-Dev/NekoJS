package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class RecipeJsonBuilder {
    private final JsonObject json = new JsonObject();
    private final RecipeEventJS event;

    public RecipeJsonBuilder(RecipeEventJS event, String type) {
        this.event = event;
        json.addProperty("type", type);
    }

    public RecipeJsonBuilder input(String key, Ingredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder output(String key, ItemStack result) {
        if (result != null) json.add(key, event.serializeResult(result));
        return this;
    }

    public RecipeJsonBuilder property(String key, Number value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder property(String key, String value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder property(String key, Boolean value) {
        json.addProperty(key, value);
        return this;
    }

    // 允许传入原始 JsonObject (用于 pattern, key 等复杂结构)
    public RecipeJsonBuilder property(String key, JsonObject value) {
        json.add(key, value);
        return this;
    }

    public RecipeJsonBuilder property(String key, JsonArray value) {
        json.add(key, value);
        return this;
    }

    public void register() {
        event.custom(json);
    }
}