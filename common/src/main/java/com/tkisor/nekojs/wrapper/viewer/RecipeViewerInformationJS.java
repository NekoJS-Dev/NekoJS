package com.tkisor.nekojs.wrapper.viewer;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event object for {@code RecipeViewerEvents.addInformation}, which attaches tooltip lines to
 * entries.
 *
 * <p>Scripts attach viewer tooltip lines to an entry with {@code event.add(entry, 'text')} or
 * {@code event.addAll(entry, ...)}. The platform applies them during JEI registration
 * ({@code registerRecipes}), so the lines are refreshed on every resource reload.
 */
@Doc("Event object for RecipeViewerEvents.addInformation; attaches tooltip lines to entries.")
public final class RecipeViewerInformationJS {
    private final List<Object> entries = new ArrayList<>();
    private final List<List<String>> information = new ArrayList<>();

    /** Adds one tooltip line to an entry. */
    @Doc("Adds one tooltip line to an entry.")
    @Param(name = "entry", value = "item id string or ItemStack-like value")
    @Param(name = "info", value = "tooltip line; null/empty lines are ignored")
    public void add(Object entry, String info) {
        addAll(entry, info);
    }

    /** Adds several tooltip lines to an entry; the varargs accept a single string or a string
     * array. */
    @Doc("Adds multiple tooltip lines to an entry.")
    @Param(name = "entry", value = "item id string or ItemStack-like value")
    @Param(name = "info", value = "tooltip lines; null/empty lines are ignored")
    public void addAll(Object entry, String... info) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(info, "info");
        List<String> lines = new ArrayList<>();
        for (String line : info) {
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }
        if (!lines.isEmpty()) {
            entries.add(entry);
            information.add(List.copyOf(lines));
        }
    }

    /** Returns a read-only view of the collected entries, index-aligned with
     * {@link #information()}. */
    @Doc("Returns the collected entries, parallel to information().")
    @Return("a copy of the collected entries")
    public List<Object> entries() {
        return List.copyOf(entries);
    }

    /** Returns the tooltip lines for each entry, read-only and index-aligned with
     * {@link #entries()}. */
    @Doc("Returns the tooltip lines for each entry, parallel to entries().")
    @Return("a copy; the list at index i belongs to entries().get(i)")
    public List<List<String>> information() {
        return information.stream().map(List::copyOf).toList();
    }
}
