package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * API 符号的稳定标识，形如 {@code kind:qualifiedName}。
 *
 * @param kind          符号类别（如 {@code global}、{@code member}）
 * @param qualifiedName 限定名（如 {@code ID.of}）
 */
public record ApiSymbolId(String kind, String qualifiedName) {

    public ApiSymbolId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        if (kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (qualifiedName.isBlank()) throw new IllegalArgumentException("qualifiedName must not be blank");
    }

    /** 返回 {@code "kind:qualifiedName"} 字符串。 */
    public String value() {
        return kind + ":" + qualifiedName;
    }

    /** 解析 {@code "kind:qualifiedName"} 字符串；无 {@code ':'} 分隔符时抛异常。 */
    public static ApiSymbolId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) throw new IllegalArgumentException("symbol id must not be blank");
        int idx = raw.indexOf(':');
        if (idx < 0) throw new IllegalArgumentException("symbol id must contain ':' separator: " + raw);
        String kind = raw.substring(0, idx);
        String qualifiedName = raw.substring(idx + 1);
        return new ApiSymbolId(kind, qualifiedName);
    }

    /** 返回 {@code "kind:qualifiedName"} 字符串。 */
    @Override
    public String toString() {
        return value();
    }
}
