package com.tkisor.nekojs.api.recipe;

import java.util.List;
import java.util.Map;

public interface StableRecipeEvent {
    StableRecipeBuilder shapeless(String result, List<String> ingredients);

    int stage(Map<String, String> filter, String stage);

    int stage(Map<String, String> filter, String stage, String scope);
}
