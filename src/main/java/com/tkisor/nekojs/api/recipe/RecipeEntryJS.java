//~ mc_legacy_api
package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Script-facing wrapper around an existing recipe JSON entry, supporting in-place edits, copying, and removal. */
public class RecipeEntryJS {
    private final RecipeEventJS event;
    private Identifier id;
    private final JsonObject json;

    public RecipeEntryJS(RecipeEventJS event, Identifier id, JsonObject json) {
        this.event = event;
        this.id = id;
        this.json = json;
    }

    public String id() {
        return id.toString();
    }

    public RecipeEntryJS id(String newId) {
        Identifier parsedId = RecipeJsonBuilder.parseId(newId);
        if (parsedId == null) {
            throw new IllegalArgumentException("Invalid recipe ID: " + newId);
        }
        Identifier oldId = id;
        event.getFinalJsons().remove(oldId);
        RecipeCreationContext context = event.getRecipeContext(oldId);
        event.removeRecipeContext(oldId);
        id = parsedId;
//? if >=26 {
        IngredientActionRegistry.rekey(oldId, id);
//?}
        event.getFinalJsons().put(id, json);
        event.setRecipeContext(id, context);
        return this;
    }

    public String type() {
        if (!json.has("type") || !json.get("type").isJsonPrimitive()) return "";
        return json.get("type").getAsString();
    }

    public String group() {
        if (!json.has("group") || !json.get("group").isJsonPrimitive()) return "";
        return json.get("group").getAsString();
    }

    public RecipeEntryJS group(String group) {
        json.addProperty("group", group);
        return this;
    }

    public RecipeEntryJS validate() {
        event.validateRecipe(id, json);
        return this;
    }

    public RecipeEntryJS removeProperty(String key) {
        json.remove(key);
        return this;
    }

    public RecipeEntryJS setPath(String path, RecipeJsonValue value) {
        builder().setPath(path, value);
        return this;
    }

    public RecipeEntryJS setPaths(RecipeJsonValue values) {
        builder().setPaths(values);
        return this;
    }

    public RecipeEntryJS removePath(String path) {
        builder().removePath(path);
        return this;
    }

    public RecipeEntryJS removePaths(RecipeJsonValue paths) {
        builder().removePaths(paths);
        return this;
    }

    public RecipeEntryJS merge(JsonObject value) {
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeEntryJS merge(RecipeJsonValue value) {
        builder().merge(value);
        return this;
    }

    public JsonObject json() {
        return json;
    }

    public RecipeJsonBuilder builder() {
        return new RecipeJsonBuilder(event, json, id);
    }

    public RecipeJsonBuilder copy(String newId) {
        RecipeCreationContext context = event.getRecipeContext(id);
        String type = type().isEmpty() ? "unknown" : type();
        RecipeCreationContext copyContext = RecipeCreationContext.of("recipe.copy", type, context != null ? context.prefix() : "copy");
        RecipeJsonBuilder builder = new RecipeJsonBuilder(event, json.deepCopy(), "copy", copyContext);
        return builder.id(newId);
    }

    public void remove() {
        event.getFinalJsons().remove(id);
        event.removeRecipeContext(id);
    }
}
