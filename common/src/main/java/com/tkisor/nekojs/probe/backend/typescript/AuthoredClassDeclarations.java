package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeSlot;

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
            if (!hidden.contains(target) && !context.config().isExcluded(target)) {
                active.put(target, entry);
            }
        }
        for (ClassDeclarationCatalogEntry entry : active.values()) {
            if (!classes.contains(entry.targetType().getName())) continue;
            for (Class<?> imported : entry.imports().stream().sorted(Comparator.comparing(Class::getName)).toList()) {
                String name = imported.getName();
                if (context.config().isExcluded(name) || hidden.contains(name)) {
                    throw missingDependency(entry, name);
                }
                classes.add(name);
            }
        }
        return new AuthoredClassDeclarations(active);
    }

    ClassDeclarationCatalogEntry get(String fqn) {
        return entries.get(fqn);
    }

    /** Reads only constant names for generator-owned aliases, without loading unrelated member signatures. */
    static TypeDecl enumAliasType(Class<?> target) {
        TypeDecl declaration = new TypeDecl(TypeDecl.Kind.ENUM, target, target.getName());
        for (var field : target.getDeclaredFields()) {
            if (!field.isEnumConstant()) continue;
            FieldDecl constant = new FieldDecl(field.getName(), TypeSlot.of(target, TypeReflector.toRef(target)));
            constant.isEnumConstant = true;
            declaration.fields.add(constant);
        }
        declaration.fields.sort(Comparator.comparing(field -> field.name));
        return declaration;
    }

    void validateGeneratedImports(IndexFileGenerator generator) {
        for (ClassDeclarationCatalogEntry entry : entries.values()) {
            if (!generator.hasDeclaration(entry.targetType().getName())) continue;
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
