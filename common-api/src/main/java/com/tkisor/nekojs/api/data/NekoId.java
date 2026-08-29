package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;

/**
 * 资源标识符（{@code namespace:path}），脚本侧类型 {@code NekoId}。
 *
 * <p>不可变；两个字段均不能为空白。无命名空间前缀的字符串在 {@link #of(String)} 中
 * 使用默认命名空间 {@link #DEFAULT_NAMESPACE}。
 *
 * @param namespace 命名空间
 * @param path      路径
 */
@ContractReceiver
public record NekoId(String namespace, String path) {
    /** 缺省命名空间。 */
    public static final String DEFAULT_NAMESPACE = "nekojs";

    public NekoId {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("ID namespace cannot be blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("ID path cannot be blank");
        }
    }

    /** 解析字符串；无 {@code ':'} 分隔符时使用默认命名空间 {@link #DEFAULT_NAMESPACE}。 */
    public static NekoId of(String value) {
        int separator = value.indexOf(':');
        if (separator >= 0) {
            return new NekoId(value.substring(0, separator), value.substring(separator + 1));
        }
        return new NekoId(DEFAULT_NAMESPACE, value);
    }

    /** 以显式命名空间与路径构造。 */
    public static NekoId of(String namespace, String path) {
        return new NekoId(namespace, path);
    }

    /** 返回命名空间。 */
    public String getNamespace() {
        return namespace;
    }

    /** 返回路径。 */
    public String getPath() {
        return path;
    }

    /** 返回 {@code "namespace:path"} 字符串表示。 */
    public String asString() {
        return toString();
    }

    /** 返回 {@code "namespace:path"} 字符串表示。 */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
