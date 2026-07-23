package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

/**
 * 1.12.2 implementation of {@link RecipeSchemaHost}, bridging the common recipe namespace
 * proxies to the 1.12.2 platform.
 *
 * <p>Since 1.12.2 RecipeEventJS is a stub without native serialization helpers,
 * this host uses {@link RecipeJsonValueConverter} directly for all JSON conversion.
 */
public final class RecipeEventSchemaHost implements RecipeSchemaHost {
    private final RecipeEventJS event;

    public RecipeEventSchemaHost(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public RecipeBuilder builder(String type, String prefix) {
        return new SimpleRecipeJsonBuilder(type, prefix);
    }

    @Override
    public Object custom(JsonObject json) {
        // 1.12.2: register custom recipe via the event stub
        return json;
    }

    @Override
    public JsonElement toJson(Value value) {
        return RecipeJsonValueConverter.toJson(value);
    }

    @Override
    public JsonElement encodeField(RecipeFieldKind kind, Value value) {
        switch (kind) {
            case JSON:
                return RecipeJsonValueConverter.toJson(value);
            case STRING:
                return new JsonPrimitive(value.asString());
            case INT:
                return new JsonPrimitive(value.asInt());
            case NUMBER:
                return new JsonPrimitive(value.asDouble());
            case BOOLEAN:
                return new JsonPrimitive(value.asBoolean());
            case INGREDIENT: {
                Ingredient ingredient = IngredientResolver.fromValue(value);
                return RecipeJsonValueConverter.toJson(ingredient);
            }
            case ITEM_STACK: {
                ItemStack stack = new ItemStackAdapter().apply(value);
                return RecipeJsonValueConverter.toJson(stack);
            }
            case FLUID_STACK:
            case FLUID_INGREDIENT:
            case SIZED_FLUID_INGREDIENT:
                // 1.12.2: fluid fields fall back to JSON conversion
                return RecipeJsonValueConverter.toJson(value);
            default:
                return RecipeJsonValueConverter.toJson(value);
        }
    }

    /**
     * Minimal RecipeBuilder for 1.12.2 that wraps a JsonObject.
     */
    private record SimpleRecipeJsonBuilder(JsonObject json, String type, String prefix) implements RecipeBuilder {
        SimpleRecipeJsonBuilder(String type, String prefix) {
            this(new JsonObject(), type, prefix);
            json.addProperty("type", type);
        }

        @Override
        public SimpleRecipeJsonBuilder setPath(String path, RecipeJsonValue value) {
            JsonElement element = RecipeJsonValueConverter.toJson(value);
            setJsonPath(json, path, element);
            return this;
        }

        private static void setJsonPath(JsonObject root, String path, JsonElement value) {
            String[] parts = path.split("\\.");
            JsonObject current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String key = parts[i];
                if (!current.has(key) || !current.get(key).isJsonObject()) {
                    JsonObject next = new JsonObject();
                    current.add(key, next);
                    current = next;
                } else {
                    current = current.getAsJsonObject(key);
                }
            }
            current.add(parts[parts.length - 1], value);
        }
    }
}
