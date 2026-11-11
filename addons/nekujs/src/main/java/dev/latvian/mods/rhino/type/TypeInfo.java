package dev.latvian.mods.rhino.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Objects;

/** Bounded native type metadata; this interface does not implement a JavaScript engine. */
public interface TypeInfo {
    TypeInfo NONE = UnknownTypeInfo.INSTANCE;
    TypeInfo STRING = of(String.class);

    static TypeInfo of(Class<?> type) {
        return type.isEnum() ? new EnumTypeInfo(type) : new NativeTypeInfo(type, List.of());
    }
    static TypeInfo of(Type type) {
        if (type instanceof Class<?> raw) return of(raw);
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return new NativeTypeInfo(raw, java.util.Arrays.stream(parameterized.getActualTypeArguments()).map(TypeInfo::of).toList());
        }
        if (type instanceof WildcardType wildcard) {
            Type[] lower = wildcard.getLowerBounds();
            if (lower.length != 0) return of(lower[0]);
            Type[] upper = wildcard.getUpperBounds();
            // The legacy metadata contract selects a lower bound first and preserves an unknown sentinel for ?.
            return upper.length == 0 || upper[0] == Object.class ? NONE : of(upper[0]);
        }
        throw new UnsupportedOperationException("[NEKO-4260] Unsupported legacy type metadata: " + type);
    }
    Class<?> asClass();
    default TypeInfo or(TypeInfo other) {
        Objects.requireNonNull(other);
        if (equals(other)) return this;
        var alternatives = new java.util.ArrayList<TypeInfo>();
        if (this instanceof UnionTypeInfo union) alternatives.addAll(union.alternatives());
        else alternatives.add(this);
        if (other instanceof UnionTypeInfo union) alternatives.addAll(union.alternatives());
        else alternatives.add(other);
        return new UnionTypeInfo(alternatives.stream().distinct().toList());
    }
    record UnionTypeInfo(List<TypeInfo> alternatives) implements TypeInfo {
        public UnionTypeInfo { alternatives = List.copyOf(alternatives); }
        @Override public Class<?> asClass() { return Object.class; }
    }
    default boolean is(TypeInfo other) {
        if (this == NONE || other == NONE) return this == other;
        if (this instanceof UnionTypeInfo || other instanceof UnionTypeInfo) return equals(other);
        return asClass() == other.asClass();
    }
    default TypeInfo param(int index) { throw new IndexOutOfBoundsException(index); }
    default TypeInfo withParams(TypeInfo... parameters) { return new NativeTypeInfo(asClass(), List.of(parameters)); }

    record NativeTypeInfo(Class<?> asClass, List<TypeInfo> parameters) implements TypeInfo {
        public NativeTypeInfo { Objects.requireNonNull(asClass); parameters = List.copyOf(parameters); }
        @Override public TypeInfo param(int index) { return parameters.get(index); }
    }
}
