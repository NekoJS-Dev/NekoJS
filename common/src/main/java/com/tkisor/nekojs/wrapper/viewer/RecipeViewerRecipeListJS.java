package com.tkisor.nekojs.wrapper.viewer;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event object for {@code RecipeViewerEvents.removeRecipes}.
 *
 * <p>Scripts collect the recipes to hide by id with {@code event.remove('minecraft:xxx')}.
 * {@code removeFromCategory(category, recipe)} optionally narrows one removal to a single
 * category, such as {@code 'minecraft:crafting'}. After the event, the platform looks the
 * recipes up per category and hides them.
 */
@Doc("Event object for RecipeViewerEvents.removeRecipes; collects recipe ids to hide.")
public final class RecipeViewerRecipeListJS {
    private Object category;
    private final List<Object> recipes = new ArrayList<>();

    /** Removes a recipe from all categories. */
    @Doc("Removes a recipe from all categories.")
    @Param(name = "recipe", value = "recipe id like 'minecraft:xxx'")
    public void remove(Object recipe) {
        recipes.add(Objects.requireNonNull(recipe, "recipe"));
    }

    /** Removes a recipe from one category only; the category is a viewer category id such as
     * {@code 'minecraft:crafting'}. */
    @Doc("Removes recipes only from the given category; the last call's category wins.")
    @Param(name = "category", value = "viewer category id like 'minecraft:crafting'")
    @Param(name = "recipe", value = "recipe id like 'minecraft:xxx'")
    public void removeFromCategory(Object category, Object recipe) {
        this.category = Objects.requireNonNull(category, "category");
        recipes.add(Objects.requireNonNull(recipe, "recipe"));
    }

    /** Returns the target category, or {@code null} when none was set. */
    @Doc("Returns the targeted category, when one was set via removeFromCategory.")
    @Return("the last targeted category, or null when remove() is used")
    public Object category() {
        return category;
    }

    /** The collected recipes, as a read-only view. */
    @Doc("Returns the collected recipes.")
    @Return("a copy of the collected recipes")
    public List<Object> recipes() {
        return List.copyOf(recipes);
    }
}
