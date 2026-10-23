package com.tkisor.nekojs.core.reflect;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retains captured owner parameters when Java member classes are represented as flat declarations. */
public final class JavaGenericTypes {
    private JavaGenericTypes() {}

    /** Returns outer parameters before member parameters, stopping capture at static boundaries. */
    public static List<TypeVariable<?>> parameters(Class<?> type) {
        if (!capturesOwner(type)) return List.of(type.getTypeParameters());
        List<TypeVariable<?>> parameters = new ArrayList<>(parameters(type.getDeclaringClass()));
        parameters.addAll(List.of(type.getTypeParameters()));
        return List.copyOf(parameters);
    }

    /** Returns matching actual arguments; raw use sites keep an empty list for the renderer's erasure policy. */
    public static List<Type> arguments(Type type) {
        if (!(type instanceof ParameterizedType parameterized)) return List.of();
        if (!(parameterized.getRawType() instanceof Class<?> raw) || !capturesOwner(raw)) {
            return List.of(parameterized.getActualTypeArguments());
        }
        List<Type> arguments = new ArrayList<>();
        arguments.addAll(arguments(parameterized.getOwnerType()));
        arguments.addAll(List.of(parameterized.getActualTypeArguments()));
        return List.copyOf(arguments);
    }

    /** Allocates distinct names by declaration identity, without changing ordinary Java parameter names. */
    public static String parameterName(TypeVariable<?> variable) {
        if (!(variable.getGenericDeclaration() instanceof Class<?> declaring)) return variable.getName();
        if (!capturesOwner(declaring)) return variable.getName();
        Set<String> reserved = new HashSet<>();
        for (TypeVariable<?> parameter : declaring.getTypeParameters()) reserved.add(parameter.getName());
        Map<TypeVariable<?>, String> names = new LinkedHashMap<>();
        Set<String> occupied = new HashSet<>();
        if (capturesOwner(declaring)) {
            for (TypeVariable<?> parameter : parameters(declaring.getDeclaringClass())) {
                occupied.add(parameterName(parameter));
            }
        }
        reserved.addAll(occupied);
        for (TypeVariable<?> parameter : declaring.getTypeParameters()) {
            String name = parameter.getName();
            if (!occupied.add(name)) {
                int suffix = 1;
                while (reserved.contains(name + suffix)) suffix++;
                name += suffix;
                reserved.add(name);
                occupied.add(name);
            }
            names.put(parameter, name);
        }
        return names.getOrDefault(variable, variable.getName());
    }

    private static boolean capturesOwner(Class<?> type) {
        return type.isMemberClass() && !Modifier.isStatic(type.getModifiers());
    }
}
