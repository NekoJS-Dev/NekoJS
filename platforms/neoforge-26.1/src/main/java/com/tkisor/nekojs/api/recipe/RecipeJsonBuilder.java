package com.tkisor.nekojs.api.recipe;

import com.google.gson.*;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

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

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, Identifier currentId) {
        this.event = event;
        this.json = prebuiltJson;
        this.currentId = currentId;
    }

    public static Identifier parseId(String id) {
        if (id.contains(":")) {
            return Identifier.tryParse(id);
        }
        return Identifier.fromNamespaceAndPath("nekojs", id);
    }

    public RecipeJsonBuilder id(String newId) {
        event.getFinalJsons().remove(this.currentId);

        Identifier parsedId = parseId(newId);

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

    public RecipeJsonBuilder merge(JsonObject value) {
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeJsonBuilder merge(Map<String, ?> value) {
        JsonElement element = convertToJsonElement(value);
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
            case IngredientJS ing -> {
                return event.serializeIngredient(ing.unwrap());
            }
            case Ingredient ing -> {
                return event.serializeIngredient(ing);
            }
            case FluidIngredientJS ing -> {
                return event.serializeFluidIngredient(ing.unwrap());
            }
            case FluidIngredient ing -> {
                return event.serializeFluidIngredient(ing);
            }
            case SizedFluidIngredient ing -> {
                return event.serializeSizedFluidIngredient(ing);
            }
            case FluidStack stack -> {
                return event.serializeFluidStack(stack);
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