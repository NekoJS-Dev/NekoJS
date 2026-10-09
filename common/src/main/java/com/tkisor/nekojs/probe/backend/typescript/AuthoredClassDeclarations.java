package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ir.TypeDecl;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Owns authored replacement selection and the required dependency checks for one render. */
final class AuthoredClassDeclarations {
    private final Map<String, ClassDeclarationCatalogEntry> entries;

    private AuthoredClassDeclarations(Map<String, ClassDeclarationCatalogEntry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    static AuthoredClassDeclarations prepare(ProbeContext context, Set<String> classes) {
        Set<String> hidden = new LinkedHashSet<>();
        if (context.ir() != null) {
            for (TypeDecl type : context.ir()) {
                if (type.hidden) hidden.add(type.fqn);
            }
        }
        Map<String, ClassDeclarationCatalogEntry> active = new LinkedHashMap<>();
        for (ClassDeclarationCatalogEntry entry : context.snapshot().classDeclarations()) {
            String target = entry.targetType().getName();
            if (classes.contains(target) && !hidden.contains(target) && !context.config().isExcluded(target)) {
                active.put(target, entry);
            }
        }
        boolean missingImports = active.values().stream().flatMap(entry -> entry.imports().stream())
                .anyMatch(type -> !classes.contains(type.getName()));
        if (missingImports) {
            // Production has already collected these seeds. Direct render contexts can omit them.
            // Reuse the same bounded scan rather than growing a backend-only dependency graph.
            for (Class<?> type : ProbeCoordinator.collectClasses(context.snapshot(), context.config())) {
                classes.add(type.getName());
            }
        }
        for (ClassDeclarationCatalogEntry entry : active.values()) {
            for (Class<?> imported : entry.imports().stream().sorted(Comparator.comparing(Class::getName)).toList()) {
                String name = imported.getName();
                if (!classes.contains(name) || context.config().isExcluded(name) || hidden.contains(name)) {
                    throw missingDependency(entry, name);
                }
            }
        }
        return new AuthoredClassDeclarations(active);
    }

    ClassDeclarationCatalogEntry get(String fqn) {
        return entries.get(fqn);
    }

    void validateGeneratedImports(IndexFileGenerator generator) {
        for (ClassDeclarationCatalogEntry entry : entries.values()) {
            for (Class<?> imported : entry.imports().stream().sorted(Comparator.comparing(Class::getName)).toList()) {
                if (!generator.hasDeclaration(imported.getName())) {
                    throw missingDependency(entry, imported.getName());
                }
            }
        }
    }

    private static IllegalArgumentException missingDependency(ClassDeclarationCatalogEntry entry, String name) {
        return new IllegalArgumentException("[NEKO-4033] Class declaration generation rejected for "
                + entry.targetType().getName() + "; required type is unavailable, excluded, or hidden: " + name);
    }
}
