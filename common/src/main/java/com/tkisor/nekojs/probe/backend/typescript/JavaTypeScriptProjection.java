package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Projects Java Object values and raw generic use sites without changing shared or explicitly edited IR. */
public final class JavaTypeScriptProjection {
    private JavaTypeScriptProjection() {}

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
