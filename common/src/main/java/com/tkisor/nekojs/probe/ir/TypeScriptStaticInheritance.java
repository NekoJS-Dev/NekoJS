package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects Java static field hiding without imposing TypeScript's static-side subtyping. */
final class TypeScriptStaticInheritance {
    record Projection(List<MethodDecl> methods) {}

    private final Map<String, TypeDecl> declarations;

    TypeScriptStaticInheritance(Map<String, TypeDecl> declarations) {
        this.declarations = Map.copyOf(declarations);
    }

    Projection project(TypeDecl declaration) {
        if (declaration.kind != TypeDecl.Kind.CLASS) return null;
        Set<String> fields = new HashSet<>();
        Set<String> exportedFields = new HashSet<>();
        for (FieldDecl field : declaration.fields) {
            if (!field.isStatic) continue;
            fields.add(field.name);
            exportedFields.add(field.effectiveName());
        }
        if (fields.isEmpty()) return null;
        List<TypeDecl> parents = parents(declaration);
        boolean shadowsField = parents.stream().flatMap(parent -> parent.fields.stream())
                .anyMatch(field -> field.isStatic && !field.hidden
                        && (fields.contains(field.name) || exportedFields.contains(field.effectiveName())));
        if (!shadowsField) return null;

        List<MethodDecl> methods = new ArrayList<>();
        Set<MethodKey> seen = new LinkedHashSet<>();
        List<TypeDecl> lineage = new ArrayList<>();
        lineage.add(declaration);
        lineage.addAll(parents);
        for (TypeDecl owner : lineage) {
            for (MethodDecl method : owner.methods) {
                if (!method.isStatic || method.isConstructor) continue;
                // Hidden declarations still hide the same Java signature on a more distant ancestor.
                if (seen.add(key(method)) && !method.hidden) methods.add(method);
            }
        }
        // Graal inherits callable static methods, but not parent static fields on the child class value.
        return new Projection(List.copyOf(methods));
    }

    private List<TypeDecl> parents(TypeDecl declaration) {
        List<TypeDecl> parents = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(declaration.fqn);
        TypeSlot slot = declaration.superType;
        while (slot != null && slot.ref.kind() == ApiTypeRef.Kind.SYMBOL) {
            String symbol = slot.ref.name();
            String fqn = symbol.substring(symbol.indexOf(':') + 1);
            if (!visited.add(fqn)) break;
            TypeDecl parent = declarations.get(fqn);
            if (parent == null || parent.hidden || parent.kind != TypeDecl.Kind.CLASS) break;
            parents.add(parent);
            slot = parent.superType;
        }
        return parents;
    }

    private record MethodKey(String name, List<Object> parameters) {}

    private static MethodKey key(MethodDecl method) {
        if (method.sourceMethod != null) {
            return new MethodKey(method.sourceMethod.getName(),
                    java.util.Arrays.stream(method.sourceMethod.getParameterTypes()).map(type -> (Object) type).toList());
        }
        return new MethodKey(method.name, method.params.stream().map(parameter -> {
            Type source = parameter.type.sourceType;
            return source == null ? (Object) parameter.type.ref : erased(source);
        }).toList());
    }

    private static Class<?> erased(Type type) {
        if (type instanceof Class<?> cls) return cls;
        if (type instanceof ParameterizedType parameterized) return erased(parameterized.getRawType());
        if (type instanceof GenericArrayType array) return Array.newInstance(erased(array.getGenericComponentType()), 0).getClass();
        if (type instanceof TypeVariable<?> variable && variable.getBounds().length != 0) return erased(variable.getBounds()[0]);
        return Object.class;
    }
}
