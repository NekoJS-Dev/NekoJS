package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;

/**
 * Platform implementation of {@link RecipeSchemaHost}, bridging the common recipe namespace
 * proxies to the NeoForge {@link RecipeEventJS} and its resolvers/serializers.
 *
 * <p>This is the single place that knows how to turn a JS value into recipe JSON for each
 * {@link RecipeFieldKind} (resolving ingredients/fluids and serializing Minecraft stacks).
 */
final class RecipeEventSchemaHost implements RecipeSchemaHost {
    private final RecipeEventJS event;

    RecipeEventSchemaHost(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public RecipeBuilder builder(String type, String prefix) {
        return new RecipeJsonBuilder(event, type, prefix);
    }

    @Override
    public Object custom(JsonObject json) {
        return event.custom(json);
    }

    @Override
    public JsonElement toJson(Value value) {
        return RecipeJsonValueConverter.toJson(event, value);
    }

    @Override
    public JsonElement encodeField(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case JSON -> RecipeJsonValueConverter.toJson(event, value);
            case STRING -> new JsonPrimitive(value.asString());
            case INT -> new JsonPrimitive(value.asInt());
            case NUMBER -> new JsonPrimitive(value.asDouble());
            case BOOLEAN -> new JsonPrimitive(value.asBoolean());
            case INGREDIENT -> event.serializeIngredient(IngredientResolver.fromValue(value));
            case ITEM_STACK -> event.serializeResult(new ItemStackAdapter().apply(value));
            case FLUID_STACK -> event.serializeFluidStack(FluidResolver.stackFromValue(value));
            case FLUID_INGREDIENT -> event.serializeFluidIngredient(FluidResolver.ingredientFromValue(value));
            case SIZED_FLUID_INGREDIENT -> event.serializeSizedFluidIngredient(FluidResolver.sizedFromValue(value));
        };
    }
}
