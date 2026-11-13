package com.tkisor.nekojs.api.surface;

import java.util.*;

/**
 * Platform-independent reference to an API type, used to describe types in contracts and
 * signatures (including generic arguments and callback signatures).
 *
 * <p>Instances are built through the static factories, and the constructor enforces the field
 * rules for each {@link Kind}: ARRAY needs exactly one element, UNION needs at least two members
 * that are deduplicated and sorted, CALLBACK needs a callback signature, and VOID carries no extra
 * data.
 *
 * <p>{@link #compatibilityKey()} returns a stable string used to compare compatibility across
 * platforms. Immutable.
 *
 * @param kind              type kind
 * @param name              type name (set for PRIMITIVE/SYMBOL/TYPE_VARIABLE, {@code null} otherwise)
 * @param arguments         generic or member arguments (used by ARRAY/UNION)
 * @param callbackSignature callback signature (used by CALLBACK, {@code null} otherwise)
 */
public record ApiTypeRef(Kind kind, String name, List<ApiTypeRef> arguments,
                         ApiSignature callbackSignature) {

    /** The kind of type. */
    public enum Kind { PRIMITIVE, SYMBOL, ARRAY, UNION, CALLBACK, VOID, TYPE_VARIABLE }

    public ApiTypeRef {
        Objects.requireNonNull(kind, "kind");
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        switch (kind) {
            case PRIMITIVE, TYPE_VARIABLE -> {
                requireName(name);
                if (!arguments.isEmpty() || callbackSignature != null) {
                    throw new IllegalArgumentException(kind + " type cannot have arguments or callback signature");
                }
            }
            case SYMBOL -> {
                // SYMBOL may carry generic arguments (such as Map<K, V>): the probe IR uses this to carry
                // parameterized Java types without loss, and each language renderer chooses the output
                // form (TS $Map<$K, $V>, sugar in Python).
                requireName(name);
                if (callbackSignature != null) {
                    throw new IllegalArgumentException("symbol type cannot have a callback signature");
                }
            }
            case ARRAY -> {
                if (name != null || arguments.size() != 1 || callbackSignature != null) {
                    throw new IllegalArgumentException("array type requires exactly one element type");
                }
            }
            case UNION -> {
                if (name != null || callbackSignature != null) {
                    throw new IllegalArgumentException("union type cannot have a name or callback signature");
                }
                arguments = arguments.stream().distinct()
                        .sorted(Comparator.comparing(ApiTypeRef::compatibilityKey)).toList();
                if (arguments.size() < 2) {
                    throw new IllegalArgumentException("union requires at least two members");
                }
            }
            case CALLBACK -> {
                if (name != null || !arguments.isEmpty() || callbackSignature == null) {
                    throw new IllegalArgumentException("callback type requires a callback signature");
                }
            }
            case VOID -> {
                if (name != null || !arguments.isEmpty() || callbackSignature != null) {
                    throw new IllegalArgumentException("void type cannot have additional data");
                }
            }
        }
    }

    /** Creates a primitive type reference (such as {@code int} or {@code string}). */
    public static ApiTypeRef primitive(String name) {
        return new ApiTypeRef(Kind.PRIMITIVE, requireName(name), List.of(), null);
    }

    /** Creates a symbol type reference named by an {@link ApiSymbolId}. */
    public static ApiTypeRef symbol(ApiSymbolId id) {
        return new ApiTypeRef(Kind.SYMBOL, Objects.requireNonNull(id, "id").value(), List.of(), null);
    }

    /** Creates a symbol type reference with generic arguments (such as {@code Map<K, V>});
     * the probe IR uses it to carry parameterized Java types without loss. */
    public static ApiTypeRef symbol(ApiSymbolId id, List<ApiTypeRef> arguments) {
        return new ApiTypeRef(Kind.SYMBOL, Objects.requireNonNull(id, "id").value(),
                arguments == null ? List.of() : List.copyOf(arguments), null);
    }

    /**
     * A declared type variable (e.g. Java generic parameter {@code T}). Used by the probe IR to
     * faithfully capture generic signatures; the {@code name} is the variable's identifier.
     */
    public static ApiTypeRef typeVariable(String name) {
        return new ApiTypeRef(Kind.TYPE_VARIABLE, requireName(name), List.of(), null);
    }

    /** Creates an array type reference for the given element type. */
    public static ApiTypeRef array(ApiTypeRef element) {
        return new ApiTypeRef(Kind.ARRAY, null, List.of(Objects.requireNonNull(element, "element")), null);
    }

    /** Creates a union type reference: members are deduplicated and sorted by compatibility key,
     * and at least two are required. */
    public static ApiTypeRef union(List<ApiTypeRef> members) {
        List<ApiTypeRef> normalized = members.stream()
                .distinct()
                .sorted(Comparator.comparing(ApiTypeRef::compatibilityKey))
                .toList();
        if (normalized.size() < 2) throw new IllegalArgumentException("union requires at least two members");
        return new ApiTypeRef(Kind.UNION, null, normalized, null);
    }

    /** Creates a callback type reference that carries the callback signature. */
    public static ApiTypeRef callback(ApiSignature signature) {
        return new ApiTypeRef(Kind.CALLBACK, null, List.of(), Objects.requireNonNull(signature, "signature"));
    }

    /** Creates the void type reference. */
    public static ApiTypeRef voidType() {
        return new ApiTypeRef(Kind.VOID, null, List.of(), null);
    }

    /** Returns a stable string used to compare compatibility across platforms. */
    public String compatibilityKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(kind.name());
        if (name != null) sb.append(':').append(name);
        if (!arguments.isEmpty()) {
            sb.append('<');
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(arguments.get(i).compatibilityKey());
            }
            sb.append('>');
        }
        if (callbackSignature != null) {
            sb.append(callbackSignature.compatibilityKey());
        }
        return sb.toString();
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("type name must not be blank");
        return name;
    }
}
