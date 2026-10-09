package com.tkisor.nekojs.api.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Replaces a Java class's generated TypeScript declaration with an authored module body.
 * The replacement is global: Java package modules are shared by every Script Type.
 * Imports identify actual Java types referenced by the body, without a module wrapper.
 * The body must export the canonical {@code $JavaName}; generated input aliases remain generator-owned.
 * A hidden IR declaration remains hidden; otherwise the authored body replaces its members.
 */
public record ClassDeclarationCatalogEntry(
        Class<?> targetType,
        String declaration,
        Set<Class<?>> imports,
        int priority
) {
    public ClassDeclarationCatalogEntry {
        Objects.requireNonNull(targetType, "[NEKO-4031] Class declaration registration rejected: targetType is required");
        if (targetType.isPrimitive() || targetType.isArray() || targetType.isLocalClass() || targetType.getSimpleName().isEmpty()
                || targetType.getPackageName().isEmpty() || declaration == null || declaration.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4031] Class declaration registration rejected: "
                    + "a named Java type and a nonempty TypeScript module body are required");
        }
        imports = Set.copyOf(imports == null ? Set.of() : imports);
        for (Class<?> imported : imports) {
            if (imported.isPrimitive() || imported.isArray() || imported.isLocalClass() || imported.getSimpleName().isEmpty()
                    || imported.getPackageName().isEmpty()) {
                throw new IllegalArgumentException("[NEKO-4031] Class declaration registration rejected: "
                        + "imports must identify named Java types: " + imported.getTypeName());
            }
        }
        declaration = declaration.strip().replace("\r\n", "\n").replace('\r', '\n');
    }

    public static ClassDeclarationCatalogEntry of(Class<?> targetType, String declaration) {
        return of(targetType, declaration, Set.of());
    }

    public static ClassDeclarationCatalogEntry of(Class<?> targetType, String declaration, Set<Class<?>> imports) {
        return new ClassDeclarationCatalogEntry(targetType, declaration, imports, 0);
    }

    /** Selects the highest priority per FQN and rejects conflicting ties independently of discovery order. */
    public static List<ClassDeclarationCatalogEntry> resolve(List<ClassDeclarationCatalogEntry> entries) {
        Map<String, NavigableMap<Integer, ClassDeclarationCatalogEntry>> grouped = new TreeMap<>();
        for (ClassDeclarationCatalogEntry entry : entries) {
            NavigableMap<Integer, ClassDeclarationCatalogEntry> priorities = grouped.computeIfAbsent(
                    entry.targetType().getName(), ignored -> new TreeMap<>());
            ClassDeclarationCatalogEntry previous = priorities.putIfAbsent(entry.priority(), entry);
            if (previous != null && (!previous.declaration().equals(entry.declaration())
                    || !previous.imports().equals(entry.imports()))) {
                throw new IllegalArgumentException("[NEKO-4032] Class declaration conflict rejected for "
                        + entry.targetType().getName() + " at priority " + entry.priority());
            }
        }
        List<ClassDeclarationCatalogEntry> resolved = new ArrayList<>();
        for (NavigableMap<Integer, ClassDeclarationCatalogEntry> priorities : grouped.values()) {
            resolved.add(priorities.lastEntry().getValue());
        }
        return List.copyOf(resolved);
    }
}
