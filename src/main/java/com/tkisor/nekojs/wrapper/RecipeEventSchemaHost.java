package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeBuilderSession;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
//? if neoforge {
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
//?}
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

    /** Creates a JSON recipe builder; delegates to {@link RecipeJsonBuilder}. */
    @Override
    public RecipeBuilder builder(String type, String prefix) {
        return new RecipeJsonBuilder(event, type, prefix);
    }

    @Override
    public RecipeBuilderSession session(RecipeBuilder builder) {
        if (!(builder instanceof RecipeJsonBuilder nativeBuilder)) {
            throw new IllegalArgumentException("[NEKO-5352] Native recipe host requires a native JSON builder");
        }
        return nativeBuilder.session();
    }

    @Override
    public Object registryAccess() {
        return event.registryAccess();
    }

    /** Adds a custom recipe JSON that skips the recipe schema. */
    @Override
    public Object custom(JsonObject json) {
        return event.custom(json);
    }

    /** Converts any JS value to recipe JSON through {@link RecipeJsonValueConverter}. */
    @Override
    public JsonElement toJson(Value value) {
        return RecipeJsonValueConverter.toJson(event, value);
    }

    /** Encodes a JS value by field kind: scalars convert directly, ingredient and fluid values
     * go through their resolvers and are then serialized. */
    @Override
    public JsonElement encodeField(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case JSON -> RecipeJsonValueConverter.toJson(event, value);
            case STRING -> new JsonPrimitive(requireString(value));
            case INT -> new JsonPrimitive(requireInt(value));
            case NUMBER -> new JsonPrimitive(requireNumber(value));
            case BOOLEAN -> new JsonPrimitive(requireBoolean(value));
            case INGREDIENT -> event.serializeIngredient(IngredientResolver.fromValue(value));
            case ITEM_STACK -> event.serializeResult(new ItemStackAdapter().apply(value));
//? if neoforge {
            case FLUID_STACK -> event.serializeFluidStack(FluidResolver.stackFromValue(value));
            case FLUID_INGREDIENT -> event.serializeFluidIngredient(FluidResolver.ingredientFromValue(value));
            case SIZED_FLUID_INGREDIENT -> event.serializeSizedFluidIngredient(FluidResolver.sizedFromValue(value));
//?} else {
/*            // fabric has no fluid recipe system (FluidResolver is not ported): fluid fields
// are not supported there
default -> throw new UnsupportedOperationException(
"fluid recipe fields are not supported on fabric yet");
 *///?}
        };
    }

    /**
     * Converts a value together with its field context, so a failure names the recipe field.
     * Array elements are reported one by one with the field name as well. Overrides the default
     * {@link RecipeSchemaHost#convertField} implementation and, when scalar or array encoding
     * fails, throws an error that carries the recipe field name, the expected type and the
     * actual type.
     */
    @Override
    public JsonElement convertField(RecipeFieldDefinition field, Value value) {
        try {
            return RecipeSchemaHost.super.convertField(field, value);
        } catch (RecipeFieldTypeException e) {
            throw new RecipeFieldTypeException(
                    "Field '" + field.name() + "' of recipe field kind " + field.kind() + ": " + e.getMessage());
        }
    }

    private static String requireString(Value value) {
        if (value.isString()) return value.asString();
        throw new RecipeFieldTypeException("expected a string but got " + describe(value));
    }

    private static int requireInt(Value value) {
        if (value.isNumber()) {
            double number = value.asDouble();
            if (number == Math.rint(number)) return value.asInt();
        }
        throw new RecipeFieldTypeException("expected an integer but got " + describe(value));
    }

    private static double requireNumber(Value value) {
        if (value.isNumber()) return value.asDouble();
        throw new RecipeFieldTypeException("expected a number but got " + describe(value));
    }

    private static boolean requireBoolean(Value value) {
        if (value.isBoolean()) return value.asBoolean();
        throw new RecipeFieldTypeException("expected a boolean but got " + describe(value));
    }

    private static String describe(Value value) {
        if (value == null) return "null";
        if (value.isNull()) return "null";
        if (value.isString()) return "string '" + value.asString() + "'";
        if (value.isBoolean()) return "boolean " + value.asBoolean();
        if (value.isNumber()) return "number " + value.asDouble();
        if (value.hasArrayElements()) return "array";
        return String.valueOf(value);
    }

    /** A type error tagged with the field context; {@code convertField} catches it and adds the
     * field name. */
    private static final class RecipeFieldTypeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RecipeFieldTypeException(String message) {
            super(message);
        }
    }
}
