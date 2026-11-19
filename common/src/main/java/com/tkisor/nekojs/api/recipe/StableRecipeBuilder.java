package com.tkisor.nekojs.api.recipe;

public interface StableRecipeBuilder {
    StableRecipeBuilder id(String id);

    StableRecipeBuilder stage(String stage);

    StableRecipeBuilder stage(String stage, String scope);
}
