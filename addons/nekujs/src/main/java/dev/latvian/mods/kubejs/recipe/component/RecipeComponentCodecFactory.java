package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.recipe.RecipeTypeRegistryContext;

@FunctionalInterface
public interface RecipeComponentCodecFactory<T extends RecipeComponent<?>> {
    MapCodec<T> create(RecipeComponentType<?> type, RecipeTypeRegistryContext ctx);
}
