package dev.latvian.mods.kubejs.recipe.schema;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.function.Supplier;

/** Original recipe subtype supplier metadata; execution is bound to the native recipe session. */
public record KubeRecipeFactory(ResourceLocation id, TypeInfo recipeType, Supplier<? extends KubeRecipe> factory) {
    public static final KubeRecipeFactory DEFAULT = new KubeRecipeFactory(
            ResourceLocation.fromNamespaceAndPath("kubejs", "basic"), KubeRecipe.class, KubeRecipe::new);

    public KubeRecipeFactory {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipeType);
        Objects.requireNonNull(factory);
    }

    public KubeRecipeFactory(ResourceLocation id, Class<?> recipeType, Supplier<? extends KubeRecipe> factory) {
        this(id, TypeInfo.of(recipeType), factory);
    }
}
