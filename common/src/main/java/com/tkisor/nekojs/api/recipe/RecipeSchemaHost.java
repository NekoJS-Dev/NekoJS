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
 * so the recipe namespace proxies ({@code RecipeNamespaceProxy}, {@code FallbackNamespaceProxy})
 * can live in {@code common} with no mod-loader dependencies.
 * Each platform supplies an implementation wrapping its {@code RecipeEventJS}.
 */
public interface RecipeSchemaHost {

    /** Create a builder for a schema-driven recipe (recipe type + id prefix). */
    RecipeBuilder builder(String type, String prefix);

    /** Borrows native recipe execution support when the platform implements it. */
    default RecipeBuilderSession session(RecipeBuilder builder) {
        throw new UnsupportedOperationException("[NEKO-5350] Native recipe builder sessions are unsupported by this host");
    }

    /** Borrows the platform registry view used by native field and recipe codecs. */
    default Object registryAccess() {
        throw new UnsupportedOperationException("[NEKO-5353] Recipe host has no borrowed registry access");
    }

    /** Register a raw/custom recipe from a prebuilt JSON object; returns the object exposed to JS. */
    Object custom(JsonObject json);

    /** Encode a single JS value to JSON according to a field kind (hides resolver + serializer). */
    JsonElement encodeField(RecipeFieldKind kind, Value value);

    /** Generic JS value → JSON conversion (the {@code JSON} field kind and raw fallback). */
    JsonElement toJson(Value value);

    /**
     * Encode the whole field using its explicit codec, or fall back to native scalar/array
     * conversion (wrapping a single value when the JS value isn't array-shaped). Shared by the
     * namespace proxies and the schema-driven builder so the conversion logic lives in one place.
     */
    default JsonElement convertField(RecipeFieldDefinition field, Value value) {
        if (field.valueCodec() != null) {
            return field.valueCodec().encoder().encode(value, this);
        }
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

    /**
     * Apply a JS value to a schema field on the platform builder.
     * Default: encode to JSON and write via {@link RecipeBuilder#setPath} (datapack-era platforms).
     * Platforms without a JSON pipeline (1.12.2) override this to apply typed values directly.
     */
    default void applyField(RecipeBuilder builder, RecipeFieldDefinition field, Value value) {
        applyEncodedField(builder, field, convertField(field, value));
    }

    /**
     * Apply prevalidated JSON without repeating conversion. Advanced schema profiles use this
     * hook after detached preparation; hosts overriding applyField alone retain the legacy path.
     */
    default void applyEncodedField(RecipeBuilder builder, RecipeFieldDefinition field, JsonElement value) {
        builder.setPath(field.path(), new RecipeJsonValue(value.deepCopy()));
    }
}
