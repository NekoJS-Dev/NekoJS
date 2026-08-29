package com.tkisor.nekojs.wrapper;

import graal.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared reflection helpers for recipe method dispatch.
 */
public final class RecipeReflectionUtil {

    private RecipeReflectionUtil() {}

    /** Indexes a class's public methods by name (skipping {@code Object} methods); overloads of
     *  one name share a list, preserving declaration order. */
    public static Map<String, List<Method>> reflectMethods(Class<?> clazz) {
        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            methods.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        return methods;
    }

    /** Indexes the target's runtime class methods by name; see {@link #reflectMethods(Class)}. */
    public static Map<String, List<Method>> reflectMethods(Object target) {
        return reflectMethods(target.getClass());
    }

    /** Converts GraalJS {@link Value} arguments to the method's parameter types, filling missing
     *  trailing arguments with {@code Optional.empty()} so optional parameters keep working. */
    public static Object[] convertArgs(Method m, Value[] args, int totalParams) {
        Class<?>[] paramTypes = m.getParameterTypes();
        Object[] converted = new Object[totalParams];
        for (int i = 0; i < totalParams; i++) {
            if (i < args.length) {
                converted[i] = args[i].as(paramTypes[i]);
            } else {
                converted[i] = Optional.empty();
            }
        }
        return converted;
    }

    /** Counts the leading parameters before the first {@code Optional}; everything from there on
     *  is treated as trailing/optional when matching call arity. */
    public static int requiredParamCount(Method m) {
        int count = 0;
        for (var p : m.getParameterTypes()) {
            if (p == Optional.class) break;
            count++;
        }
        return count;
    }
}
