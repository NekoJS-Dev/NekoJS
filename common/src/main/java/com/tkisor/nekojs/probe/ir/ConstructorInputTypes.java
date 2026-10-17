package com.tkisor.nekojs.probe.ir;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Projects constructor-local variables into usable input bounds without changing class or method variables. */
final class ConstructorInputTypes {
    private ConstructorInputTypes() {}

    static Type project(Type type) {
        return project(type, new HashSet<>());
    }

    private static Type project(Type type, Set<TypeVariable<?>> expanding) {
        if (type instanceof TypeVariable<?> variable
                && variable.getGenericDeclaration() instanceof Constructor<?>) {
            if (!expanding.add(variable)) return unbounded();
            try {
                Type[] bounds = variable.getBounds();
                return bounds.length == 0 || bounds[0] == Object.class ? unbounded() : project(bounds[0], expanding);
            } finally {
                expanding.remove(variable);
            }
        }
        if (type instanceof ParameterizedType parameterized) {
            Type owner = parameterized.getOwnerType();
            Type projectedOwner = owner == null ? null : project(owner, expanding);
            Type[] arguments = Arrays.stream(parameterized.getActualTypeArguments())
                    .map(arg -> project(arg, expanding)).toArray(Type[]::new);
            return Objects.equals(owner, projectedOwner) && Arrays.equals(arguments, parameterized.getActualTypeArguments())
                    ? type : new Parameterized(parameterized.getRawType(), projectedOwner, arguments);
        }
        if (type instanceof GenericArrayType array) {
            Type component = project(array.getGenericComponentType(), expanding);
            return component.equals(array.getGenericComponentType()) ? type : new Array(component);
        }
        if (type instanceof WildcardType wildcard) {
            Type[] upper = Arrays.stream(wildcard.getUpperBounds()).map(bound -> project(bound, expanding)).toArray(Type[]::new);
            Type[] lower = Arrays.stream(wildcard.getLowerBounds()).map(bound -> project(bound, expanding)).toArray(Type[]::new);
            return Arrays.equals(upper, wildcard.getUpperBounds()) && Arrays.equals(lower, wildcard.getLowerBounds())
                    ? type : new Wildcard(upper, lower);
        }
        return type;
    }

    private static WildcardType unbounded() { return new Wildcard(new Type[]{Object.class}, new Type[0]); }

    private record Parameterized(Type raw, Type owner, Type[] arguments) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return arguments.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return owner; }
        @Override public String getTypeName() {
            String base = owner == null ? raw.getTypeName() : owner.getTypeName() + "$"
                    + ((Class<?>) raw).getSimpleName();
            return base + "<" + String.join(", ", Arrays.stream(arguments).map(Type::getTypeName).toList()) + ">";
        }
        @Override public boolean equals(Object other) {
            return other instanceof ParameterizedType type && Objects.equals(raw, type.getRawType())
                    && Objects.equals(owner, type.getOwnerType()) && Arrays.equals(arguments, type.getActualTypeArguments());
        }
        @Override public int hashCode() { return Arrays.hashCode(arguments) ^ Objects.hashCode(owner) ^ Objects.hashCode(raw); }
    }

    private record Array(Type component) implements GenericArrayType {
        @Override public Type getGenericComponentType() { return component; }
        @Override public String getTypeName() { return component.getTypeName() + "[]"; }
        @Override public boolean equals(Object other) {
            return other instanceof GenericArrayType type && component.equals(type.getGenericComponentType());
        }
        @Override public int hashCode() { return component.hashCode(); }
    }

    private record Wildcard(Type[] upper, Type[] lower) implements WildcardType {
        @Override public Type[] getUpperBounds() { return upper.clone(); }
        @Override public Type[] getLowerBounds() { return lower.clone(); }
        @Override public String getTypeName() {
            if (lower.length > 0) return "? super " + lower[0].getTypeName();
            return upper.length == 0 || upper[0] == Object.class ? "?" : "? extends " + upper[0].getTypeName();
        }
        @Override public boolean equals(Object other) {
            return other instanceof WildcardType type && Arrays.equals(upper, type.getUpperBounds())
                    && Arrays.equals(lower, type.getLowerBounds());
        }
        @Override public int hashCode() { return Arrays.hashCode(upper) ^ Arrays.hashCode(lower); }
    }
}
