package com.tkisor.nekojs.api.surface;

import java.util.*;

/**
 * 平台无关的 API 类型引用，用于契约/签名中描述类型（含泛型参数、回调签名等）。
 *
 * <p>通过静态工厂构造；构造时按 {@link Kind} 强校验各字段约束：ARRAY 须恰好一个元素、
 * UNION 至少两个成员且去重排序、CALLBACK 须携带回调签名、VOID 不得有额外数据。
 *
 * <p>{@link #compatibilityKey()} 生成用于跨平台兼容性比对的稳定字符串。不可变。
 *
 * @param kind             类型种类
 * @param name             类型名（PRIMITIVE/SYMBOL/TYPE_VARIABLE 时有效，其余为 {@code null}）
 * @param arguments        泛型/成员参数（ARRAY/UNION 时使用）
 * @param callbackSignature 回调签名（CALLBACK 时使用，其余为 {@code null}）
 */
public record ApiTypeRef(Kind kind, String name, List<ApiTypeRef> arguments,
                         ApiSignature callbackSignature) {

    /** 类型种类。 */
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
                // SYMBOL 允许携带泛型实参（如 Map<K, V>）：probe IR 用它无损承载参数化 Java 类型，
                // 各语言渲染器自行决定渲染形态（TS 的 $Map<$K, $V>、Python 的语法糖）。
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

    /** 构造原始类型引用（如 {@code int}、{@code string}）。 */
    public static ApiTypeRef primitive(String name) {
        return new ApiTypeRef(Kind.PRIMITIVE, requireName(name), List.of(), null);
    }

    /** 构造符号类型引用（以 {@link ApiSymbolId} 命名）。 */
    public static ApiTypeRef symbol(ApiSymbolId id) {
        return new ApiTypeRef(Kind.SYMBOL, Objects.requireNonNull(id, "id").value(), List.of(), null);
    }

    /** 构造带泛型实参的符号类型引用（如 {@code Map<K, V>}）——probe IR 无损承载参数化 Java 类型用。 */
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

    /** 构造数组类型引用（元素类型）。 */
    public static ApiTypeRef array(ApiTypeRef element) {
        return new ApiTypeRef(Kind.ARRAY, null, List.of(Objects.requireNonNull(element, "element")), null);
    }

    /** 构造联合类型引用（去重并按兼容性键排序，至少两个成员）。 */
    public static ApiTypeRef union(List<ApiTypeRef> members) {
        List<ApiTypeRef> normalized = members.stream()
                .distinct()
                .sorted(Comparator.comparing(ApiTypeRef::compatibilityKey))
                .toList();
        if (normalized.size() < 2) throw new IllegalArgumentException("union requires at least two members");
        return new ApiTypeRef(Kind.UNION, null, normalized, null);
    }

    /** 构造回调类型引用（携带回调签名）。 */
    public static ApiTypeRef callback(ApiSignature signature) {
        return new ApiTypeRef(Kind.CALLBACK, null, List.of(), Objects.requireNonNull(signature, "signature"));
    }

    /** 构造 void 类型引用。 */
    public static ApiTypeRef voidType() {
        return new ApiTypeRef(Kind.VOID, null, List.of(), null);
    }

    /** 返回用于跨平台兼容性比对的稳定字符串。 */
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
