package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;

/** Projects Java Object values without changing explicit structured-object IR or the shared reflection model. */
public final class JavaObjectTypeProjection {
    private JavaObjectTypeProjection() {}

    /** Applies only where the original Java type still identifies Object, including nested host arguments. */
    public static ApiTypeRef project(Type source, ApiTypeRef reference) {
        if (source == Object.class && reference.equals(ApiTypeRef.primitive("object"))) {
            // Graal Object values can be primitives or null; TypeScript object excludes both.
            return ApiTypeRef.primitive("unknown");
        }
        if (source instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper.length > 0 && upper[0] != Object.class ? project(upper[0], reference) : reference;
        }
        if (reference.kind() == ApiTypeRef.Kind.ARRAY) {
            Type element = source instanceof GenericArrayType array ? array.getGenericComponentType()
                    : source instanceof Class<?> cls && cls.isArray() ? cls.getComponentType() : null;
            if (element != null) return ApiTypeRef.array(project(element, reference.arguments().getFirst()));
        }
        if (source instanceof ParameterizedType parameterized && reference.kind() == ApiTypeRef.Kind.SYMBOL) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == reference.arguments().size()) {
                List<ApiTypeRef> projected = new ArrayList<>(arguments.length);
                for (int index = 0; index < arguments.length; index++) {
                    projected.add(project(arguments[index], reference.arguments().get(index)));
                }
                return new ApiTypeRef(reference.kind(), reference.name(), projected, reference.callbackSignature());
            }
        }
        return reference;
    }
}
