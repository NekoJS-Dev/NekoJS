package com.tkisor.nekojs.wrapper.viewer;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event object for {@code RecipeViewerEvents.removeCategories}.
 *
 * <p>Scripts collect the viewer categories to hide by id with
 * {@code event.remove('minecraft:crafting')}. After the event, the platform hides each
 * collected category.
 */
@Doc("Event object for RecipeViewerEvents.removeCategories; collects viewer category ids to hide.")
public final class RecipeViewerCategoryListJS {
    private final List<Object> categories = new ArrayList<>();

    /** Hides a category by id, such as {@code 'minecraft:crafting'}. */
    @Doc("Removes a viewer category by id.")
    @Param(name = "category", value = "viewer category id like 'minecraft:crafting'")
    public void remove(Object category) {
        categories.add(Objects.requireNonNull(category, "category"));
    }

    /** Returns a read-only view of the collected categories. */
    @Doc("Returns the collected category ids.")
    @Return("a copy of the collected categories")
    public List<Object> categories() {
        return List.copyOf(categories);
    }
}
