package com.tkisor.nekojs.api.recipe;

import com.google.gson.*;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Map;

public class RecipeJsonBuilder {
    private final JsonObject json;
    private final RecipeEventJS event;
    private Identifier currentId;

    public RecipeJsonBuilder(RecipeEventJS event, String type, String prefix) {
        this.event = event;
        this.json = new JsonObject();
        this.json.addProperty("type", type);

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, String prefix) {
        this.event = event;
        this.json = prebuiltJson;

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
    }

    public RecipeJsonBuilder id(String newId) {
        event.getFinalJsons().remove(this.currentId);

        Identifier parsedId;
        if (newId.contains(":")) {
            parsedId = Identifier.tryParse(newId);
        } else {
            parsedId = Identifier.fromNamespaceAndPath("nekojs", newId);
        }

        if (parsedId == null) {
            NekoJS.LOGGER.debug("[NekoJS] Invalid recipe ID: {}", newId);
            parsedId = this.currentId;
        }

        this.currentId = parsedId;
        event.getFinalJsons().put(this.currentId, this.json);

        return this;
    }

    public RecipeJsonBuilder group(String group) {
        return property("group", group);
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

    public RecipeJsonBuilder property(String key, JsonObject value) {
        json.add(key, value);
        return this;
    }

    public RecipeJsonBuilder property(String key, JsonArray value) {
        json.add(key, value);
        return this;
    }

    public RecipeJsonBuilder property(String key, List<?> value) {
        json.add(key, convertToJsonElement(value));
        return this;
    }

    public RecipeJsonBuilder property(String key, Map<String, ?> value) {
        json.add(key, convertToJsonElement(value));
        return this;
    }

    public RecipeJsonBuilder property(String key, Object value) {
        json.add(key, convertToJsonElement(value));
        return this;
    }

    private JsonElement convertToJsonElement(Object obj) {
        switch (obj) {
            case null -> {
                return JsonNull.INSTANCE;
            }
            case JsonElement je -> {
                return je;
            }
            case Number n -> {
                return new JsonPrimitive(n);
            }
            case String s -> {
                return new JsonPrimitive(s);
            }
            case Boolean b -> {
                return new JsonPrimitive(b);
            }
            case Character c -> {
                return new JsonPrimitive(c);
            }
            case Ingredient ing -> {
                return event.serializeIngredient(ing);
            }
            case ItemStack stack -> {
                return event.serializeResult(stack);
            }
            case List<?> list -> {
                JsonArray array = new JsonArray();
                for (Object item : list) array.add(convertToJsonElement(item));
                return array;
            }
            case Map<?, ?> map -> {
                JsonObject jsonObj = new JsonObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    jsonObj.add(String.valueOf(entry.getKey()), convertToJsonElement(entry.getValue()));
                }
                return jsonObj;
            }
            default -> {
            }
        }

        return new JsonPrimitive(obj.toString());
    }
}