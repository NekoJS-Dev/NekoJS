package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Projects Java values and raw uses while retaining source identities until their conversion boundary. */
public final class JavaTypeScriptProjection {
    // Fixed JDK metadata contains no plugin classes or generation-owned resources.
    private static final Map<String, List<ApiTypeRef>> SCALAR_ALTERNATIVES = scalarAlternatives();

    private JavaTypeScriptProjection() {}

    /** Scalar implementations of sealed platform types, alongside their ordinary host values. */
    public static List<ApiTypeRef> scalarAlternatives(String className) {
        return SCALAR_ALTERNATIVES.getOrDefault(className, List.of());
    }

    private static Map<String, List<ApiTypeRef>> scalarAlternatives() {
        Map<String, LinkedHashSet<ApiTypeRef>> alternatives = new LinkedHashMap<>();
        for (Class<?> scalar : List.of(String.class, Character.class, Boolean.class, Byte.class,
                Short.class, Integer.class, Long.class, Float.class, Double.class)) {
            ApiTypeRef value = ApiTypeRef.primitive(scalar == String.class || scalar == Character.class ? "string"
                    : scalar == Boolean.class ? "boolean" : "number");
            var parents = new ArrayDeque<Class<?>>(List.of(scalar.getInterfaces()));
            var seen = new LinkedHashSet<Class<?>>();
            while (!parents.isEmpty()) {
                Class<?> parent = parents.removeFirst();
                if (!seen.add(parent)) continue;
                if (parent.isSealed()) alternatives.computeIfAbsent(parent.getName(), ignored -> new LinkedHashSet<>()).add(value);
                parents.addAll(List.of(parent.getInterfaces()));
            }
        }
        Map<String, List<ApiTypeRef>> result = new LinkedHashMap<>();
        alternatives.forEach((name, values) -> result.put(name,
                values.stream().sorted(java.util.Comparator.comparing(ApiTypeRef::compatibilityKey)).toList()));
        return Map.copyOf(result);
    }

    /** Uses source types only for unedited slots, including nested host arguments and generic bounds. */
    public static ApiTypeRef project(Type source, ApiTypeRef reference) {
        if (source == Object.class && reference.equals(ApiTypeRef.primitive("object"))) {
            // Graal Object values can be primitives or null; TypeScript object excludes both.
            return ApiTypeRef.primitive("unknown");
        }
        if (source instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper.length > 0 && upper[0] != Object.class ? project(upper[0], reference) : reference;
        }
        if (source instanceof Class<?> cls && !JavaGenericTypes.parameters(cls).isEmpty()
                && reference.kind() == ApiTypeRef.Kind.SYMBOL && reference.arguments().isEmpty()
                && reference.name().equals("java:" + cls.getName())) {
            // A raw Java use site erases arguments; the generic declaration itself retains its parameters.
            return new ApiTypeRef(reference.kind(), reference.name(),
                    Collections.nCopies(JavaGenericTypes.parameters(cls).size(), ApiTypeRef.primitive("any")),
                    reference.callbackSignature());
        }
        if (reference.kind() == ApiTypeRef.Kind.ARRAY) {
            Type element = source instanceof GenericArrayType array ? array.getGenericComponentType()
                    : source instanceof Class<?> cls && cls.isArray() ? cls.getComponentType() : null;
            if (element != null) return ApiTypeRef.array(project(element, reference.arguments().getFirst()));
        }
        if (source instanceof ParameterizedType parameterized && reference.kind() == ApiTypeRef.Kind.SYMBOL) {
            List<Type> arguments = JavaGenericTypes.arguments(parameterized);
            if (arguments.size() == reference.arguments().size()) {
                List<ApiTypeRef> projected = new ArrayList<>(arguments.size());
                for (int index = 0; index < arguments.size(); index++) {
                    projected.add(project(arguments.get(index), reference.arguments().get(index)));
                }
                return new ApiTypeRef(reference.kind(), reference.name(), projected, reference.callbackSignature());
            }
        }
        return reference;
    }
}
