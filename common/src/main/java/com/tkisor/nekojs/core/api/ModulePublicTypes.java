package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.annotation.ScriptNativeBridge;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/** Conservative native-leak check for ordinary stable registrations, independent of managed contracts. */
public final class ModulePublicTypes {
    private ModulePublicTypes() {}

    public static void validateStable(Class<?> type, boolean statics) {
        if (!type.isAnnotationPresent(ScriptNativeBridge.class)) validate(type, statics, new HashSet<>());
    }

    private static void check(Type type) {
        String name = type.getTypeName();
        if (name.contains("net.minecraft.") || name.contains("net.neoforged.") || name.contains("net.fabricmc.")) {
            throw new IllegalArgumentException("[NEKO-4057] Stable API exposes a native type without an explicit native bridge: " + name);
        }
    }

    private static void validateType(Type type, Set<Class<?>> seen, Set<Type> visiting) {
        check(type);
        if (!visiting.add(type)) return;
        if (type instanceof Class<?> concrete) validate(concrete, false, seen);
        else if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
            validateType(parameterized.getRawType(), seen, visiting);
            for (Type argument : parameterized.getActualTypeArguments()) validateType(argument, seen, visiting);
        } else if (type instanceof java.lang.reflect.GenericArrayType array) {
            validateType(array.getGenericComponentType(), seen, visiting);
        } else if (type instanceof java.lang.reflect.WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) validateType(bound, seen, visiting);
            for (Type bound : wildcard.getLowerBounds()) validateType(bound, seen, visiting);
        } else if (type instanceof java.lang.reflect.TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) validateType(bound, seen, visiting);
        }
    }

    private static void validate(Class<?> type, boolean statics, Set<Class<?>> seen) {
        check(type);
        if (type.isArray()) { validate(type.componentType(), false, seen); return; }
        if (type.isPrimitive() || type.getPackageName().startsWith("java.")
                || type.getPackageName().startsWith("graal.") || !seen.add(type)) return;
        var members = JavaMemberIndex.exposedMembersOf(type);
        members.methods().values().forEach(methods -> methods.stream()
                .filter(method -> Modifier.isStatic(method.getModifiers()) == statics).forEach(method -> {
                    validateType(method.getGenericReturnType(), seen, new HashSet<>());
                    for (var parameter : method.getGenericParameterTypes()) validateType(parameter, seen, new HashSet<>());
                }));
        if (!statics) members.fields().values().forEach(fields -> fields.forEach(field -> {
            validateType(field.getGenericType(), seen, new HashSet<>());
        }));
    }
}
