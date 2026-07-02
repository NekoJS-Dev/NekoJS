package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

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
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOWEST;
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
