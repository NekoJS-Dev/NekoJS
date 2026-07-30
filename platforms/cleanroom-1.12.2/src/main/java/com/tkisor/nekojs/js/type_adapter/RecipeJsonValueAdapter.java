package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 RecipeJsonValue adapter - wraps JS values for recipe JSON conversion.
 * Uses {@link RecipeJsonValueConverter#wrap} which delegates to the platform-specific converter.
 */
public final class RecipeJsonValueAdapter implements JSTypeAdapter<RecipeJsonValue> {
    @Override
    public Class<RecipeJsonValue> getTargetClass() {
        return RecipeJsonValue.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                number(),
                bool(),
                arrayOf(self()),
                object());
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOWEST;
    }

    @Override
    public boolean test(Value value) {
        return true;
    }

    @Override
    public RecipeJsonValue apply(Value value) {
        return RecipeJsonValueConverter.wrap(value);
    }
}
