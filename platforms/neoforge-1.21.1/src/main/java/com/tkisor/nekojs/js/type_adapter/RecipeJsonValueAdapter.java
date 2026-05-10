package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
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
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOWEST;
    }

    @Override
    public boolean canConvert(Value value) {
        return true;
    }

    @Override
    public RecipeJsonValue convert(Value value) {
        return RecipeJsonValueConverter.wrap(value);
    }
}
