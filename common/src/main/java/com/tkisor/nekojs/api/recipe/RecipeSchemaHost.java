package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import graal.graalvm.polyglot.Value;

/**
 * SPI abstraction over the platform-specific recipe event plus its resolvers/serializers.
 *
 * <p>Centralizes all Minecraft/NeoForge-coupled recipe operations behind a common interface,
 * so the recipe namespace proxies ({@code RecipeNamespaceProxy}, {@code DataDrivenRecipeNamespaceProxy},
 * {@code FallbackNamespaceProxy}) can live in {@code common} with no mod-loader dependencies.
 * Each platform supplies an implementation wrapping its {@code RecipeEventJS}.
 */
public interface RecipeSchemaHost {

    /** Create a builder for a schema-driven recipe (recipe type + id prefix). */
    RecipeBuilder builder(String type, String prefix);

    /** Register a raw/custom recipe from a prebuilt JSON object; returns the object exposed to JS. */
    Object custom(JsonObject json);

    /** Encode a single JS value to JSON according to a field kind (hides resolver + serializer). */
    JsonElement encodeField(RecipeFieldKind kind, Value value);

    /** Generic JS value → JSON conversion (the {@code JSON} field kind and raw fallback). */
    JsonElement toJson(Value value);

    /**
     * Encode a JS value to JSON for a schema field: scalar via {@link #encodeField}, or an
     * array (wrapping a single value when the JS value isn't array-shaped). Shared by the
     * namespace proxies and the schema-driven builder so the conversion logic lives in one place.
     */
    default JsonElement convertField(RecipeFieldDefinition field, Value value) {
        if (!field.array()) {
            return encodeField(field.kind(), value);
        }
        JsonArray array = new JsonArray();
        if (value.hasArrayElements()) {
            for (long i = 0; i < value.getArraySize(); i++) {
                array.add(encodeField(field.kind(), value.getArrayElement(i)));
            }
        } else {
            array.add(encodeField(field.kind(), value));
        }
        return array;
    }
}
