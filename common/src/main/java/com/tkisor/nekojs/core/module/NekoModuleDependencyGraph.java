package com.tkisor.nekojs.core.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoModuleDependencyGraph {
    private final Set<String> entryModules = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> dependenciesByParent = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> parentsByDependency = new ConcurrentHashMap<>();

    public void clear() {
        entryModules.clear();
        dependenciesByParent.clear();
        parentsByDependency.clear();
    }

    public void markEntry(String entryId, String moduleId) {
        if (entryId == null || entryId.isBlank() || moduleId == null || moduleId.isBlank()) return;
        entryModules.add(moduleId);
    }

    public void recordDependency(String parentId, String childId) {
        if (parentId == null || parentId.isBlank() || childId == null || childId.isBlank()) return;
        dependenciesByParent.computeIfAbsent(parentId, ignored -> ConcurrentHashMap.newKeySet()).add(childId);
        parentsByDependency.computeIfAbsent(childId, ignored -> ConcurrentHashMap.newKeySet()).add(parentId);
    }

    public void clearDependencies(String parentId) {
        if (parentId == null || parentId.isBlank()) return;
        Set<String> children = dependenciesByParent.remove(parentId);
        if (children == null) return;
        for (String child : children) {
            Set<String> parents = parentsByDependency.get(child);
            if (parents != null) {
                parents.remove(parentId);
                if (parents.isEmpty()) {
                    parentsByDependency.remove(child);
                }
            }
        }
    }

    public List<String> affectedEntries(String moduleId) {
        return affectedModules(moduleId).stream().filter(entryModules::contains).toList();
    }

    public List<String> affectedModules(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return List.of();
        Set<String> modules = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(moduleId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!modules.add(current)) {
                continue;
            }
            Set<String> parents = parentsByDependency.getOrDefault(current, Collections.emptySet());
            queue.addAll(parents);
        }
        return new ArrayList<>(modules);
    }
}
