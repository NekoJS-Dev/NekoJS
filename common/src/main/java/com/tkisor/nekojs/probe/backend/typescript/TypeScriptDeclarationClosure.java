package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.core.error.Diagnostics;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Completes actual declaration references independently of the bounded primary scan. */
final class TypeScriptDeclarationClosure {
    private final ProbeContext context;
    private final IndexFileGenerator generator;
    private final Map<String, TypeDecl> declarations = new LinkedHashMap<>();
    private final Map<String, ClassDeclarationCatalogEntry> replacements = new LinkedHashMap<>();
    private final Map<String, String> unavailable = new LinkedHashMap<>();
    private final Map<String, List<String>> incompleteMembers = new LinkedHashMap<>();
    private final Set<String> hidden = new LinkedHashSet<>();
    private final ClassLoader loader;

    TypeScriptDeclarationClosure(ProbeContext context, IndexFileGenerator generator) {
        this(context, generator, Thread.currentThread().getContextClassLoader());
    }

    TypeScriptDeclarationClosure(ProbeContext context, IndexFileGenerator generator, ClassLoader loader) {
        this.context = context;
        this.generator = generator;
        this.loader = loader;
        if (context.ir() != null) {
            for (TypeDecl declaration : context.ir()) {
                declarations.put(declaration.fqn, declaration);
                if (declaration.hidden) hidden.add(declaration.fqn);
            }
        }
        for (var replacement : context.snapshot().classDeclarations()) {
            replacements.put(replacement.targetType().getName(), replacement);
        }
    }

    /** Re-renders cached IR after alias preparation; reflection happens at most once per class per render. */
    void expand(Set<String> classes) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> pending = new TreeSet<>(classes);
        while (!pending.isEmpty()) {
            String name = pending.iterator().next();
            pending.remove(name);
            if (!visited.add(name) || unavailable.containsKey(name)) continue;
            if (hidden.contains(name)) {
                continue;
            }
            if (context.config().isExcluded(name)) {
                unavailable.put(name, "excluded");
                continue;
            }
            ClassDeclarationCatalogEntry replacement = replacements.get(name);
            if (replacement != null) {
                Set<String> imports = new LinkedHashSet<>();
                for (Class<?> imported : replacement.imports()) {
                    String dependency = imported.getName();
                    if (hidden.contains(dependency) || context.config().isExcluded(dependency)) {
                        throw new IllegalArgumentException("[NEKO-4033] Class declaration generation rejected for "
                                + name + "; required type is unavailable, excluded, or hidden: " + dependency);
                    }
                    imports.add(dependency);
                }
                TypeDecl enumeration = replacement.targetType().isEnum()
                        ? AuthoredClassDeclarations.enumAliasType(replacement.targetType()) : null;
                generator.predeclareClassDeclaration(name, replacement.declaration(), imports, enumeration);
            } else {
                TypeDecl declaration;
                try {
                    declaration = declarations.get(name);
                    if (declaration == null) {
                        declaration = new TypeReflector().reflect(Class.forName(name, false, loader));
                        declarations.put(name, declaration);
                    }
                } catch (RuntimeException | LinkageError | ClassNotFoundException failure) {
                    unavailable.put(name, failure.toString());
                    Diagnostics.report("probe-typescript", Diagnostics.Severity.WARN,
                            "[NEKO-4038] Probe declaration skipped: class=" + name
                                    + "; declaration omitted from generated TypeScript", failure);
                    continue;
                }
                Set<String> imports = declaration.mutated
                        ? ProbeModifyTypeEventJS.collectEditedSymbolFqns(declaration, packageOf(name)) : Set.of();
                generator.predeclareClass(name, declaration, imports);
                if (!declaration.memberOmissions.isEmpty()) {
                    incompleteMembers.put(name, List.copyOf(declaration.memberOmissions));
                }
            }
            // Reference caches are sets; sort before insertion to keep package member order stable across JVMs.
            for (String dependency : new TreeSet<>(generator.referencedClasses(name))) {
                if (includeReference(classes, dependency)) pending.add(dependency);
            }
        }
    }

    /** Releases reflected types after all declaration rendering has completed. */
    void clearCaches() { declarations.clear(); }

    Map<String, String> unavailable() { return Map.copyOf(unavailable); }

    Map<String, List<String>> incompleteMembers() { return Map.copyOf(incompleteMembers); }

    boolean includeReference(Set<String> classes, String dependency) {
        if (hidden.contains(dependency) || context.config().isExcluded(dependency)) {
            unavailable.put(dependency, hidden.contains(dependency) ? "hidden" : "excluded");
            return false;
        }
        return classes.add(dependency);
    }

    private static String packageOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }
}
