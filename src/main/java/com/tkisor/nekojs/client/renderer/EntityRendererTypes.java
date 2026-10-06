package com.tkisor.nekojs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/** Checks a native renderer's entity generic before installing its erased callback. */
final class EntityRendererTypes {
    private EntityRendererTypes() {}

    static void requireCompatible(Class<?> renderer, Class<?> entityClass) {
        Map<TypeVariable<?>, Type> arguments = new HashMap<>();
        Class<?> current = renderer;
        while (current != null && current != EntityRenderer.class) {
            Type parent = current.getGenericSuperclass();
            if (parent instanceof ParameterizedType parameterized) {
                current = (Class<?>) parameterized.getRawType();
                TypeVariable<?>[] variables = current.getTypeParameters();
                Type[] supplied = parameterized.getActualTypeArguments();
                for (int index = 0; index < variables.length; index++) {
                    arguments.put(variables[index], resolve(supplied[index], arguments));
                }
            } else if (parent instanceof Class<?> rawClass) {
                current = rawClass;
            } else {
                current = null;
            }
        }
        Type accepted = resolve(EntityRenderer.class.getTypeParameters()[0], arguments);
        if (!(accepted instanceof Class<?> acceptedClass) || entityClass == null || !acceptedClass.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("[NEKO-4025] Entity renderer " + renderer.getName()
                    + " is incompatible with " + (entityClass == null ? "an untyped factory" : entityClass.getName()));
        }
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> arguments) {
        while (type instanceof TypeVariable<?> variable && arguments.containsKey(variable)) {
            Type replacement = arguments.get(variable);
            if (replacement == type) {
                break;
            }
            type = replacement;
        }
        return type;
    }
}
