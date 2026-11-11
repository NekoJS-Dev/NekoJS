package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonElement;
import java.util.Map;

/** Creates an additional fluent target over the already prepared native recipe builder. */
@FunctionalInterface
public interface RecipeExecutionFactory {
    Object create(RecipeBuilder builder, RecipeSchemaHost host, RecipeBuilderSession session);

    /** Validates detached, encoded field-name values before the native builder is allocated. */
    default void validatePreparedFields(Map<String, JsonElement> fields) {
    }

    /** Declares the additional fluent target type without constructing a runtime recipe. */
    default Class<?> exposedType() {
        return Object.class;
    }
}
