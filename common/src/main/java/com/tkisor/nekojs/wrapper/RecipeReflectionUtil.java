package com.tkisor.nekojs.wrapper;

import graal.graalvm.polyglot.Value;
import com.tkisor.nekojs.api.annotation.HideFromJS;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared reflection helpers for recipe method dispatch.
 */
public final class RecipeReflectionUtil {

    private RecipeReflectionUtil() {}

    /** Indexes visible public methods by name, preserving reflection order for overload dispatch. */
    public static Map<String, List<Method>> reflectMethods(Class<?> clazz) {
        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class || isHiddenMethod(m)) continue;
            methods.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        return methods;
    }

    /** Indexes the target's runtime class methods by name; see {@link #reflectMethods(Class)}. */
    public static Map<String, List<Method>> reflectMethods(Object target) {
        return reflectMethods(target.getClass());
    }

    /** Recipe lifecycle hooks stay hidden when an external subtype overrides their implementation. */
    public static boolean isHiddenMethod(Method method) {
        if (method.isBridge() || method.isSynthetic() || method.isAnnotationPresent(HideFromJS.class)
                || method.getDeclaringClass().isAnnotationPresent(HideFromJS.class)) return true;
        if (Modifier.isStatic(method.getModifiers())) return false;
        return hasHiddenDeclaration(method.getDeclaringClass(), method, new java.util.HashSet<>());
    }

    private static boolean hasHiddenDeclaration(Class<?> type, Method method, java.util.Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return false;
        try {
            Method declaration = type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            if (!Modifier.isPrivate(declaration.getModifiers()) && !Modifier.isStatic(declaration.getModifiers())
                    && (declaration.isAnnotationPresent(HideFromJS.class) || type.isAnnotationPresent(HideFromJS.class))) {
                return true;
            }
        } catch (NoSuchMethodException absent) {
            // Intermediate classes need not redeclare the inherited hook.
        }
        if (hasHiddenDeclaration(type.getSuperclass(), method, visited)) return true;
        for (Class<?> contract : type.getInterfaces()) {
            if (hasHiddenDeclaration(contract, method, visited)) return true;
        }
        return false;
    }

    /** Converts arguments, filling missing trailing optionals. Returns {@code null} only when
     *  a value cannot map to its parameter type; context and guest conversion failures propagate. */
    public static Object[] convertArgs(Method m, Value[] args, int totalParams) {
        Class<?>[] paramTypes = m.getParameterTypes();
        Type[] genericTypes = m.getGenericParameterTypes();
        if (args.length < requiredParamCount(m) || args.length > totalParams) return null;
        Object[] converted = new Object[totalParams];
        for (int i = 0; i < totalParams; i++) {
            if (i < args.length) {
                try {
                    if (paramTypes[i].isPrimitive() && args[i].isNull()) {
                        // Box null conversion so native adapters run without primitive unboxing failure.
                        converted[i] = args[i].as(boxedPrimitive(paramTypes[i]));
                        if (converted[i] == null) return null;
                    } else {
                        converted[i] = convert(args[i], genericTypes[i]);
                    }
                } catch (ClassCastException mismatch) {
                    // Registered adapters preserve execution failures before Graal normalizes target mismatches.
                    return null;
                }
            } else {
                if (paramTypes[i] != Optional.class) return null;
                converted[i] = Optional.empty();
            }
        }
        return converted;
    }

    /** Traverses guest containers only; leaf values still use the current context's registered adapters. */
    private static Object convert(Value value, Type type) {
        Class<?> raw = rawType(type);
        if (value.isHostObject() || (value.isNull() && raw != Optional.class)) return value.as(raw);
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (raw == Optional.class) {
                return value.isNull() ? Optional.empty() : Optional.ofNullable(convert(value, arguments[0]));
            }
            if (raw == List.class || raw == Collection.class || raw == Iterable.class) {
                if (!value.hasArrayElements()) return value.as(raw);
                var result = new ArrayList<>();
                for (long index = 0; index < value.getArraySize(); index++) {
                    result.add(convert(value.getArrayElement(index), arguments[0]));
                }
                return result;
            }
            if (raw == Map.class) {
                var result = new LinkedHashMap<>();
                if (value.hasHashEntries()) {
                    Value entries = value.getHashEntriesIterator();
                    while (entries.hasIteratorNextElement()) {
                        Value entry = entries.getIteratorNextElement();
                        result.put(convert(entry.getArrayElement(0), arguments[0]),
                                convert(entry.getArrayElement(1), arguments[1]));
                    }
                } else if (value.hasMembers()) {
                    for (String key : value.getMemberKeys()) {
                        result.put(convert(value.getContext().asValue(key), arguments[0]),
                                convert(value.getMember(key), arguments[1]));
                    }
                } else {
                    return value.as(raw);
                }
                return result;
            }
        } else if (type instanceof GenericArrayType array && value.hasArrayElements()) {
            Type component = array.getGenericComponentType();
            Object result = Array.newInstance(rawType(component), Math.toIntExact(value.getArraySize()));
            for (int index = 0; index < Array.getLength(result); index++) {
                Array.set(result, index, convert(value.getArrayElement(index), component));
            }
            return result;
        }
        return value.as(raw);
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> raw) return raw;
        if (type instanceof ParameterizedType parameterized) return (Class<?>) parameterized.getRawType();
        if (type instanceof TypeVariable<?> variable) return rawType(variable.getBounds()[0]);
        if (type instanceof WildcardType wildcard) return rawType(wildcard.getUpperBounds()[0]);
        if (type instanceof GenericArrayType array) return Array.newInstance(rawType(array.getGenericComponentType()), 0).getClass();
        return Object.class;
    }

    private static Class<?> boxedPrimitive(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    /** Invokes a selected method without reclassifying its execution failure as an overload mismatch. */
    static Object invoke(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException invocation) {
            Throwable cause = invocation.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new IllegalStateException("[NEKO-5200] Recipe method failed with a checked exception: "
                    + method.toGenericString(), cause);
        } catch (IllegalAccessException access) {
            throw new IllegalStateException("[NEKO-5201] Recipe method is not accessible: "
                    + method.toGenericString(), access);
        }
    }

    /** Only a suffix consisting entirely of Optional parameters may be omitted. */
    public static int requiredParamCount(Method m) {
        Class<?>[] parameters = m.getParameterTypes();
        int required = parameters.length;
        while (required > 0 && parameters[required - 1] == Optional.class) required--;
        return required;
    }
}
