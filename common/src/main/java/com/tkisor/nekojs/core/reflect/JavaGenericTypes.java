package com.tkisor.nekojs.core.reflect;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import java.lang.reflect.WildcardType;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
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

    /** Erases a resolved signature parameter, including generic arrays and the first variable bound. */
    public static Class<?> erased(Type type) {
        if (type instanceof Class<?> cls) return cls;
        if (type instanceof ParameterizedType parameterized) return erased(parameterized.getRawType());
        if (type instanceof GenericArrayType array) return Array.newInstance(erased(array.getGenericComponentType()), 0).getClass();
        if (type instanceof TypeVariable<?> variable && variable.getBounds().length != 0) return erased(variable.getBounds()[0]);
        return Object.class;
    }

    /** Returns outer parameters before member parameters and stops collecting at static boundaries. */
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
        for (TypeVariable<?> parameter : parameters(declaring.getDeclaringClass())) {
            occupied.add(parameterName(parameter));
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
        // Core reflection interns one TypeVariable instance per class parameter, so the map always holds it.
        return names.get(variable);
    }

    /** Converts Java types to the neutral declaration model without losing owner arguments. */
    public static ApiTypeRef toTypeRef(Type type) {
        return toTypeRef(type, false);
    }

    /** Resolves unbound variables to their JVM bounds for callables that declare no TypeScript type parameters. */
    public static ApiTypeRef toErasedTypeRef(Type type) {
        return toTypeRef(type, true);
    }

    private static ApiTypeRef toTypeRef(Type type, boolean eraseVariables) {
        if (type == null || type == void.class || type == Void.class) return ApiTypeRef.voidType();
        if (type instanceof Class<?> cls) {
            if (cls == String.class || cls == char.class) return ApiTypeRef.primitive("string");
            if (cls == boolean.class || cls == Boolean.class) return ApiTypeRef.primitive("boolean");
            if (cls == float.class || cls == Float.class || cls == double.class || cls == Double.class)
                return ApiTypeRef.primitive("float");
            if (cls.isPrimitive() || Number.class.isAssignableFrom(cls)) return ApiTypeRef.primitive("int");
            if (cls == Object.class) return ApiTypeRef.primitive("object");
            if (cls.isArray()) return ApiTypeRef.array(toTypeRef(cls.getComponentType(), eraseVariables));
            List<ApiTypeRef> arguments = eraseVariables
                    ? parameters(cls).stream().map(ignored -> ApiTypeRef.primitive("any")).toList() : List.of();
            return ApiTypeRef.symbol(new ApiSymbolId("java", cls.getName()), arguments);
        }
        if (type instanceof ParameterizedType parameterized) {
            if (!(parameterized.getRawType() instanceof Class<?> raw)) return ApiTypeRef.primitive("any");
            return ApiTypeRef.symbol(new ApiSymbolId("java", raw.getName()), arguments(parameterized).stream()
                    .map(argument -> toTypeRef(argument, eraseVariables)).toList());
        }
        if (type instanceof GenericArrayType array) return ApiTypeRef.array(toTypeRef(array.getGenericComponentType(), eraseVariables));
        if (type instanceof TypeVariable<?> variable) return eraseVariables
                ? toTypeRef(erased(variable), true) : ApiTypeRef.typeVariable(parameterName(variable));
        if (type instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) return toTypeRef(upper[0], eraseVariables);
        }
        return ApiTypeRef.primitive("any");
    }

    private static boolean capturesOwner(Class<?> type) {
        return type.isMemberClass() && !Modifier.isStatic(type.getModifiers());
    }
}
