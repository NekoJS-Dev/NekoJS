package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Map;

public class RecipeJsonBuilder {
    private final JsonObject json;
    private final RecipeEventJS event;
    private final RecipeCreationContext context;
    private Identifier currentId;

    public RecipeJsonBuilder(RecipeEventJS event, String type, String prefix) {
        this(event, type, prefix, RecipeCreationContext.of("event.builder", type, prefix));
    }

    public RecipeJsonBuilder(RecipeEventJS event, String type, String prefix, RecipeCreationContext context) {
        this.event = event;
        this.json = new JsonObject();
        this.json.addProperty("type", type);
        this.context = context;

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, String prefix) {
        this(event, prebuiltJson, prefix, RecipeCreationContext.of("event.custom", recipeType(prebuiltJson), prefix));
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, String prefix, RecipeCreationContext context) {
        this.event = event;
        this.json = prebuiltJson;
        this.context = context;

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, Identifier currentId) {
        this(event, prebuiltJson, currentId, event.getRecipeContext(currentId));
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, Identifier currentId, RecipeCreationContext context) {
        this.event = event;
        this.json = prebuiltJson;
        this.currentId = currentId;
        this.context = context != null ? context : RecipeCreationContext.of("existing", recipeType(prebuiltJson), "existing");
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public static Identifier parseId(String id) {
        if (id.contains(":")) {
            return Identifier.tryParse(id);
        }
        return Identifier.fromNamespaceAndPath("nekojs", id);
    }

    public RecipeJsonBuilder id(String newId) {
        event.getFinalJsons().remove(this.currentId);
        event.removeRecipeContext(this.currentId);

        Identifier parsedId = parseId(newId);

        if (parsedId == null) {
            NekoJS.LOGGER.debug("[NekoJS] Invalid recipe ID: {}", newId);
            parsedId = this.currentId;
        }

        this.currentId = parsedId;
        event.getFinalJsons().put(this.currentId, this.json);
        event.setRecipeContext(this.currentId, this.context);

        return this;
    }

    public RecipeJsonBuilder group(String group) {
        json.addProperty("group", group);
        return this;
    }

    public RecipeJsonBuilder validate() {
        event.validateRecipe(currentId, json);
        return this;
    }

    public JsonObject json() {
        return json;
    }

    public RecipeJsonBuilder removeProperty(String key) {
        json.remove(key);
        return this;
    }

    public RecipeJsonBuilder setPath(String path, RecipeJsonValue value) {
        RecipeJsonPath.set(json, path, RecipeJsonValueConverter.toJson(event, value));
        return this;
    }

    public RecipeJsonBuilder setPaths(RecipeJsonValue values) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, values);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Recipe JSON batch path values must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            RecipeJsonPath.set(json, entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeJsonBuilder removePath(String path) {
        RecipeJsonPath.remove(json, path);
        return this;
    }

    public RecipeJsonBuilder removePaths(RecipeJsonValue paths) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, paths);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Recipe JSON batch remove paths must be an array");
        }
        for (JsonElement path : element.getAsJsonArray()) {
            removePath(path.getAsString());
        }
        return this;
    }

    public RecipeJsonBuilder merge(JsonObject value) {
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeJsonBuilder merge(RecipeJsonValue value) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, value);
        if (element.isJsonObject()) {
            return merge(element.getAsJsonObject());
        }
        return this;
    }

    public RecipeJsonBuilder input(String key, Ingredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder output(String key, ItemStack result) {
        if (result != null) json.add(key, event.serializeResult(result));
        return this;
    }

    public RecipeJsonBuilder fluid(String key, FluidStack stack) {
        if (stack != null) json.add(key, event.serializeFluidStack(stack));
        return this;
    }

    public RecipeJsonBuilder fluidIngredient(String key, FluidIngredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeFluidIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder sizedFluidIngredient(String key, SizedFluidIngredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeSizedFluidIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder property(String key, RecipeJsonValue value) {
        json.add(key, RecipeJsonValueConverter.toJson(event, value));
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, JsonElement value) {
        json.add(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, Number value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, String value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, Boolean value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeCreationContext context() {
        return context;
    }

    private static String recipeType(JsonObject json) {
        if (json != null && json.has("type") && json.get("type").isJsonPrimitive()) {
            return json.get("type").getAsString();
        }
        return "unknown";
    }
}
