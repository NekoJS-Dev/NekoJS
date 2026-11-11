package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import graal.graalvm.polyglot.Value;

import java.util.Objects;

/** Whole-field conversion with an optional nominal component input type and structural tooling fallback. */
public record RecipeFieldValueCodec(AdapterInputShape inputShape, ApiTypeRef inputType, Encoder encoder) {
    public RecipeFieldValueCodec(AdapterInputShape inputShape, Encoder encoder) {
        this(inputShape, null, encoder);
    }

    public RecipeFieldValueCodec {
        Objects.requireNonNull(inputShape, "inputShape");
        Objects.requireNonNull(encoder, "encoder");
    }

    @FunctionalInterface
    public interface Encoder {
        JsonElement encode(Value value, RecipeSchemaHost host);
    }
}
