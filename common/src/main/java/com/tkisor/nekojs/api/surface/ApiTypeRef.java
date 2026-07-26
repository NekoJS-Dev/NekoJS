package com.tkisor.nekojs.api.surface;

import java.util.*;

public record ApiTypeRef(Kind kind, String name, List<ApiTypeRef> arguments,
                         ApiSignature callbackSignature) {

    public enum Kind { PRIMITIVE, SYMBOL, ARRAY, UNION, CALLBACK, VOID }

    public ApiTypeRef {
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
    }

    public static ApiTypeRef primitive(String name) {
        return new ApiTypeRef(Kind.PRIMITIVE, requireName(name), List.of(), null);
    }

    public static ApiTypeRef symbol(ApiSymbolId id) {
        return new ApiTypeRef(Kind.SYMBOL, Objects.requireNonNull(id, "id").value(), List.of(), null);
    }

    public static ApiTypeRef array(ApiTypeRef element) {
        return new ApiTypeRef(Kind.ARRAY, null, List.of(Objects.requireNonNull(element, "element")), null);
    }

    public static ApiTypeRef union(List<ApiTypeRef> members) {
        List<ApiTypeRef> normalized = members.stream()
                .distinct()
                .sorted(Comparator.comparing(ApiTypeRef::compatibilityKey))
                .toList();
        if (normalized.size() < 2) throw new IllegalArgumentException("union requires at least two members");
        return new ApiTypeRef(Kind.UNION, null, normalized, null);
    }

    public static ApiTypeRef callback(ApiSignature signature) {
        return new ApiTypeRef(Kind.CALLBACK, null, List.of(), Objects.requireNonNull(signature, "signature"));
    }

    public static ApiTypeRef voidType() {
        return new ApiTypeRef(Kind.VOID, null, List.of(), null);
    }

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
