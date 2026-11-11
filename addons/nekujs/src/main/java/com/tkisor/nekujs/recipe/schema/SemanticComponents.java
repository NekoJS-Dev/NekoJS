package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import java.util.List;

/** Resolves whole component expressions without loading resources or evaluating script. */
public interface SemanticComponents {
    /** Reject unsupported expressions with {@link IllegalArgumentException}; never return a raw fallback. */
    RecipeFieldValueCodec resolve(JsonElement expression);

    /** Validate and encode a JSON literal using the same semantic component as runtime values. */
    JsonElement encodeConstant(JsonElement expression, JsonElement literal);

    /** Resolves an explicit factory/processor capability; unsupported execution must remain terminal. */
    default RecipeExecutionFactory executionFactory(JsonElement factory, List<JsonElement> processors) {
        throw new IllegalArgumentException("Recipe execution factory or processors are not supported");
    }
}
