package com.tkisor.nekujs.recipe.value;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Preserves original recipe nominal metadata without depending on a Probe backend. */
public final class LegacyRecipeInputTypes {
    private LegacyRecipeInputTypes() {}

    public static ApiTypeRef of(TypeInfo type) {
        Objects.requireNonNull(type, "type");
        if (type == TypeInfo.NONE) return ApiTypeRef.primitive("any");
        if (type instanceof TypeInfo.UnionTypeInfo union) {
            List<ApiTypeRef> alternatives = union.alternatives().stream().map(LegacyRecipeInputTypes::of).distinct().toList();
            return alternatives.size() == 1 ? alternatives.getFirst() : ApiTypeRef.union(alternatives);
        }
        Class<?> raw = type.asClass();
        if (raw.isArray()) return ApiTypeRef.array(of(raw.getComponentType()));
        ApiTypeRef scalar = scalar(raw);
        if (scalar != null) return scalar;
        List<ApiTypeRef> parameters = type instanceof TypeInfo.NativeTypeInfo nativeType && !nativeType.parameters().isEmpty()
                ? nativeType.parameters().stream().map(LegacyRecipeInputTypes::of).toList()
                : Collections.nCopies(raw.getTypeParameters().length, ApiTypeRef.primitive("any"));
        return ApiTypeRef.symbol(new ApiSymbolId("java", raw.getName()), parameters);
    }

    public static ApiTypeRef of(Class<?> type) {
        return of(TypeInfo.of(Objects.requireNonNull(type, "type")));
    }

    private static ApiTypeRef scalar(Class<?> type) {
        if (type == void.class || type == Void.class) return ApiTypeRef.voidType();
        if (type == Object.class) return ApiTypeRef.primitive("unknown");
        if (type == String.class || type == char.class || type == Character.class) return ApiTypeRef.primitive("string");
        if (type == boolean.class || type == Boolean.class) return ApiTypeRef.primitive("boolean");
        if (type == byte.class || type == short.class || type == int.class || type == long.class
                || type == float.class || type == double.class || type == Byte.class || type == Short.class
                || type == Integer.class || type == Long.class || type == Float.class || type == Double.class) {
            return ApiTypeRef.primitive("number");
        }
        return null;
    }
}
