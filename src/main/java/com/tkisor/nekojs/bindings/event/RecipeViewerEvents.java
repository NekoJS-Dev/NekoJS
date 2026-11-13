package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerCategoryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerEntryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerInformationJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerRecipeListJS;

/**
 * Recipe viewer integration events (JEI). Scripts listen from CLIENT scripts; the events are
 * fired by {@code RecipeViewerJeiPlugin} when JEI rebuilds at runtime (resource reload).
 *
 * <p>Matches KubeJS RecipeViewerEvents, trimmed down: entry events dispatch by type
 * ({@code 'item'} / {@code 'fluid'}), recipe and category events dispatch by id.
 */
public interface RecipeViewerEvents {
    EventGroup GROUP = EventGroup.of("RecipeViewerEvents");

    /** Entry type key: scripts dispatch on it with {@code RecipeViewerEvents.addEntries('item', ...)}. */
    DispatchKey<RecipeViewerEntryListJS, String> ENTRY_TYPE_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(RecipeViewerEntryListJS event) {
            return event.getType();
        }
    };

    /** Adds entries to the viewer. Scripts pass item or fluid ids, or objects. */
    EventBusJS<RecipeViewerEntryListJS, String> ADD_ENTRIES =
            GROUP.client("addEntries", RecipeViewerEntryListJS.class, ENTRY_TYPE_KEY);

    /** Removes entries from the viewer. This is not a permanent removal: other paths can still
     * make them visible. */
    EventBusJS<RecipeViewerEntryListJS, String> REMOVE_ENTRIES =
            GROUP.client("removeEntries", RecipeViewerEntryListJS.class, ENTRY_TYPE_KEY);

    /** Hides recipes by recipe id; the call can be narrowed to a category. */
    EventBusJS<RecipeViewerRecipeListJS, Void> REMOVE_RECIPES =
            GROUP.client("removeRecipes", RecipeViewerRecipeListJS.class);

    /** Hides a whole viewer category by category id. */
    EventBusJS<RecipeViewerCategoryListJS, Void> REMOVE_CATEGORIES =
            GROUP.client("removeCategories", RecipeViewerCategoryListJS.class);

    /** Attaches tooltip information to entries. Applied during JEI registration and refreshed
     * after a resource reload. */
    EventBusJS<RecipeViewerInformationJS, Void> ADD_INFORMATION =
            GROUP.client("addInformation", RecipeViewerInformationJS.class);
}
