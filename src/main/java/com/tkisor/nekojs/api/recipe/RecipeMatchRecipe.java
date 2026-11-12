package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.Objects;

/** Read-only recipe identity exposed to a synchronous filter callback. */
public final class RecipeMatchRecipe {
    private final RecipeHolder<?> holder;

    public RecipeMatchRecipe(RecipeHolder<?> holder) {
        this.holder = Objects.requireNonNull(holder, "holder");
    }

    public String getOrCreateId() { return RecipeEventJS.recipeHolderId(holder).toString(); }
    public String getMod() { return RecipeEventJS.recipeHolderId(holder).getNamespace(); }
    public String getType() { return String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer())); }
    public String getGroup() { return RecipeEventJS.recipeGroup(holder.value()); }
}
