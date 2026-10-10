package com.tkisor.nekojs.core.reflect;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.AnnotatedType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Resolves functional methods and contextual generic signatures through class and interface hierarchies. */
public final class FunctionalInterfaceResolver {
    private FunctionalInterfaceResolver() {}

    /** A method signature with type variables substituted from the declared hierarchy. */
    public record Signature(Method method, List<Type> parameterTypes, Type returnType,
                            List<TypeVariable<?>> typeParameters) {
        public Signature {
            parameterTypes = List.copyOf(parameterTypes);
            typeParameters = List.copyOf(typeParameters);
        }

        public Signature(Method method, List<Type> parameterTypes, Type returnType) {
            this(method, parameterTypes, returnType, List.of(method.getTypeParameters()));
        }
    }

    /** Returns the resolved SAM signature, or null when {@code declared} is not a functional interface. */
    public static Signature resolve(Type declared) {
        Class<?> raw = rawClass(declared);
        Method sam = singleAbstractMethod(raw);
        if (sam == null) return null;

        return resolve(declared, sam);
    }

    /** Resolves an inherited method against a class or interface's actual generic hierarchy. */
    public static Signature resolve(Type declared, Method method) {
        Map<TypeVariable<?>, Type> arguments = interfaceArguments(declared, method.getDeclaringClass(), Map.of(),
                new HashSet<>());
        if (arguments == null) return null;

        Set<String> classNames = new HashSet<>();
        Class<?> host = rawClass(declared);
        if (host != null) {
            for (TypeVariable<?> variable : host.getTypeParameters()) classNames.add(variable.getName());
        }
        Set<String> usedNames = new HashSet<>(classNames);
        for (TypeVariable<?> variable : method.getTypeParameters()) usedNames.add(variable.getName());
        List<TypeVariable<?>> variables = new java.util.ArrayList<>();
        for (TypeVariable<?> variable : method.getTypeParameters()) {
            String name = variable.getName();
            if (classNames.contains(name)) {
                int suffix = 1;
                while (usedNames.contains(name + suffix)) suffix++;
                name += suffix;
                usedNames.add(name);
            }
            // Context substitution can introduce a class variable with the same spelling as a method variable.
            // Preserve their distinct identities in both the signature and its method bounds.
            TypeVariable<?> resolved = new ResolvedTypeVariable(variable, name, arguments);
            arguments.put(variable, resolved);
            variables.add(resolved);
        }

        List<Type> parameters = Arrays.stream(method.getGenericParameterTypes())
                .map(type -> substitute(type, arguments, new HashSet<>()))
                .toList();
        Type result = substitute(method.getGenericReturnType(), arguments, new HashSet<>());
        return new Signature(method, parameters, result, variables);
    }

    /** Returns the raw class for class and parameterized reflection types. */
    public static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> raw) return raw;
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof GenericArrayType array) {
            Class<?> component = rawClass(array.getGenericComponentType());
            return component == null ? null : Array.newInstance(component, 0).getClass();
        }
        return null;
    }

    private static Method singleAbstractMethod(Class<?> raw) {
        if (raw == null || !raw.isInterface()) return null;
        Map<MethodSignature, Method> methods = new HashMap<>();
        Method[] candidates = raw.getMethods();
        Arrays.sort(candidates, Comparator.comparing(Method::toGenericString));
        for (Method method : candidates) {
            if (!Modifier.isAbstract(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.isDefault() || isObjectMethod(method)) continue;
            MethodSignature signature = new MethodSignature(method.getName(), List.of(method.getParameterTypes()));
            Method previous = methods.get(signature);
            if (previous == null) {
                methods.put(signature, method);
            } else if (previous.getReturnType().isAssignableFrom(method.getReturnType())) {
                methods.put(signature, method);
            } else if (!method.getReturnType().isAssignableFrom(previous.getReturnType())) {
                return null;
            }
        }
        return methods.size() == 1 ? methods.values().iterator().next() : null;
    }

    private static boolean isObjectMethod(Method method) {
        try {
            Method objectMethod = Object.class.getMethod(method.getName(), method.getParameterTypes());
            return objectMethod.getReturnType() == method.getReturnType();
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private record MethodSignature(String name, List<Class<?>> parameters) {}

    private static Map<TypeVariable<?>, Type> interfaceArguments(Type declared, Class<?> target,
                                                                  Map<TypeVariable<?>, Type> inherited,
                                                                  Set<Class<?>> path) {
        Class<?> raw = rawClass(declared);
        if (raw == null || !path.add(raw)) return null;
        try {
            Map<TypeVariable<?>, Type> arguments = new HashMap<>(inherited);
            if (declared instanceof ParameterizedType parameterized) {
                TypeVariable<?>[] variables = raw.getTypeParameters();
                Type[] actual = parameterized.getActualTypeArguments();
                for (int index = 0; index < variables.length; index++) {
                    arguments.put(variables[index], substitute(actual[index], inherited, new HashSet<>()));
                }
            }
            if (raw == target) return arguments;

            Type[] parents = raw.getGenericInterfaces();
            Arrays.sort(parents, Comparator.comparing(Type::getTypeName));
            for (Type parent : parents) {
                Map<TypeVariable<?>, Type> resolved = interfaceArguments(parent, target, arguments, path);
                if (resolved != null) return resolved;
            }
            Type superclass = raw.getGenericSuperclass();
            return superclass == null ? null : interfaceArguments(superclass, target, arguments, path);
        } finally {
            path.remove(raw);
        }
    }

    private static Type substitute(Type declared, Map<TypeVariable<?>, Type> arguments,
                                   Set<TypeVariable<?>> visited) {
        if (declared instanceof TypeVariable<?> variable) {
            if (!visited.add(variable)) return variable;
            Type replacement = arguments.get(variable);
            // Actual arguments have already been resolved as each inherited hierarchy edge is visited.
            // Do not substitute inside a replacement again: a nested type can mention the same class
            // TypeVariable as the raw interface (Function<R, V> inside Function<T, R>), where that
            // occurrence belongs to the enclosing declaration, not the nested raw type's argument map.
            return replacement == null ? variable : replacement;
        }
        if (declared instanceof ParameterizedType parameterized) {
            Type[] actual = parameterized.getActualTypeArguments();
            Type[] resolved = new Type[actual.length];
            for (int index = 0; index < actual.length; index++) {
                resolved[index] = substitute(actual[index], arguments, new HashSet<>(visited));
            }
            Type owner = parameterized.getOwnerType() == null ? null
                    : substitute(parameterized.getOwnerType(), arguments, new HashSet<>(visited));
            return new ResolvedParameterizedType(parameterized.getRawType(), owner, resolved);
        }
        if (declared instanceof GenericArrayType array) {
            Type component = substitute(array.getGenericComponentType(), arguments, new HashSet<>(visited));
            if (component instanceof Class<?> cls) return java.lang.reflect.Array.newInstance(cls, 0).getClass();
            return new ResolvedGenericArrayType(component);
        }
        if (declared instanceof WildcardType wildcard) {
            Type[] upper = Arrays.stream(wildcard.getUpperBounds())
                    .map(type -> substitute(type, arguments, new HashSet<>(visited))).toArray(Type[]::new);
            Type[] lower = Arrays.stream(wildcard.getLowerBounds())
                    .map(type -> substitute(type, arguments, new HashSet<>(visited))).toArray(Type[]::new);
            return new ResolvedWildcardType(upper, lower);
        }
        return declared;
    }

    private record ResolvedParameterizedType(Type rawType, Type ownerType, Type[] actualTypeArguments)
            implements ParameterizedType {
        private ResolvedParameterizedType {
            actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public String getTypeName() {
            StringJoiner arguments = new StringJoiner(", ", "<", ">");
            for (Type argument : actualTypeArguments) arguments.add(argument.getTypeName());
            return rawType.getTypeName() + arguments;
        }
    }

    private record ResolvedGenericArrayType(Type genericComponentType) implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }

        @Override
        public String getTypeName() {
            return genericComponentType.getTypeName() + "[]";
        }
    }

    private record ResolvedTypeVariable(TypeVariable<?> source, String name, Map<TypeVariable<?>, Type> arguments)
            implements TypeVariable<GenericDeclaration> {
        @Override
        public int hashCode() { return name.hashCode() ^ source.getGenericDeclaration().hashCode(); }

        @Override
        public boolean equals(Object other) {
            return other instanceof TypeVariable<?> variable && name.equals(variable.getName())
                    && source.getGenericDeclaration().equals(variable.getGenericDeclaration());
        }

        @Override
        public String toString() { return name; }

        @Override
        public Type[] getBounds() {
            return Arrays.stream(source.getBounds())
                    .map(bound -> substitute(bound, arguments, new HashSet<>())).toArray(Type[]::new);
        }

        @Override
        public GenericDeclaration getGenericDeclaration() { return source.getGenericDeclaration(); }

        @Override
        public String getName() { return name; }

        @Override
        public String getTypeName() { return name; }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return Arrays.stream(source.getAnnotatedBounds())
                    .map(bound -> new ResolvedAnnotatedType(bound,
                            substitute(bound.getType(), arguments, new HashSet<>())))
                    .toArray(AnnotatedType[]::new);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) { return source.getAnnotation(annotationClass); }

        @Override
        public Annotation[] getAnnotations() { return source.getAnnotations(); }

        @Override
        public Annotation[] getDeclaredAnnotations() { return source.getDeclaredAnnotations(); }
    }

    private record ResolvedAnnotatedType(AnnotatedType source, Type type) implements AnnotatedType {
        @Override
        public Type getType() { return type; }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) { return source.getAnnotation(annotationClass); }

        @Override
        public Annotation[] getAnnotations() { return source.getAnnotations(); }

        @Override
        public Annotation[] getDeclaredAnnotations() { return source.getDeclaredAnnotations(); }
    }

    private record ResolvedWildcardType(Type[] upperBounds, Type[] lowerBounds) implements WildcardType {
        private ResolvedWildcardType {
            upperBounds = upperBounds.clone();
            lowerBounds = lowerBounds.clone();
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public String getTypeName() {
            if (lowerBounds.length > 0) return "? super " + lowerBounds[0].getTypeName();
            if (upperBounds.length == 0 || upperBounds[0] == Object.class) return "?";
            return "? extends " + upperBounds[0].getTypeName();
        }
    }
}
