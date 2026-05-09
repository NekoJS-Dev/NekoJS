package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.event.EventGroup;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public interface NekoContextSnapshotProvider {
    NekoContextSnapshotProvider EMPTY = new NekoContextSnapshotProvider() {
        @Override
        public Map<String, EventGroup> eventGroups() {
            return Map.of();
        }

        @Override
        public Set<String> recipeNamespaces() {
            return Set.of();
        }

        @Override
        public @Nullable Class<?> recipeHandlerClass(String namespace) {
            return null;
        }
    };

    Map<String, EventGroup> eventGroups();

    Set<String> recipeNamespaces();

    @Nullable Class<?> recipeHandlerClass(String namespace);
}
