package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class NeoForgeNekoContextSnapshotProvider implements NekoContextSnapshotProvider {
    @Override
    public Map<String, EventGroup> eventGroups() {
        return NekoEventGroups.all();
    }

    @Override
    public Set<String> recipeNamespaces() {
        return NekoRecipeNamespaces.getNamespaces();
    }

    @Override
    public @Nullable Class<?> recipeHandlerClass(String namespace) {
        return NekoRecipeNamespaces.getHandlerClass(namespace);
    }
}
