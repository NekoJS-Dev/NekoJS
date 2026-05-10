package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;

import java.util.Map;

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
        event.getFinalJsons().remove(id);
        id = parsedId;
        event.getFinalJsons().put(id, json);
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
        RecipeJsonBuilder builder = new RecipeJsonBuilder(event, json.deepCopy(), "copy");
        return builder.id(newId);
    }

    public void remove() {
        event.getFinalJsons().remove(id);
    }
}
