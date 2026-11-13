package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;

/**
 * A resource identifier in {@code namespace:path} form, exposed to scripts as {@code NekoId}.
 *
 * <p>Immutable; neither field may be blank. A string without a namespace prefix gets the
 * default namespace {@link #DEFAULT_NAMESPACE} in {@link #of(String)}.
 *
 * @param namespace the namespace
 * @param path      the path
 */
@ContractReceiver
public record NekoId(String namespace, String path) {
    /** Default namespace. */
    public static final String DEFAULT_NAMESPACE = "nekojs";

    public NekoId {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("ID namespace cannot be blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("ID path cannot be blank");
        }
    }

    /** Parses a string; uses the default namespace {@link #DEFAULT_NAMESPACE} when there is no
     *  {@code ':'} separator. */
    public static NekoId of(String value) {
        int separator = value.indexOf(':');
        if (separator >= 0) {
            return new NekoId(value.substring(0, separator), value.substring(separator + 1));
        }
        return new NekoId(DEFAULT_NAMESPACE, value);
    }

    /** Creates an id from an explicit namespace and path. */
    public static NekoId of(String namespace, String path) {
        return new NekoId(namespace, path);
    }

    /** Parses a resource ID without normalizing it; unqualified values use {@code nekojs}. */
    public static NekoId resource(String value) {
        if (value == null) {
            throw new IllegalArgumentException("[NEKO-4056] Resource ID cannot be null");
        }
        int separator = value.indexOf(':');
        return separator < 0
                ? resource(DEFAULT_NAMESPACE, value)
                : resource(value.substring(0, separator), value.substring(separator + 1));
    }

    /** Constructs a resource ID using the fixed NekoJS resource grammar. */
    public static NekoId resource(String namespace, String path) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")
                || path == null || !path.matches("[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("[NEKO-4056] Resource ID requires namespace [a-z0-9_.-]+ and path [a-z0-9/._-]+");
        }
        return new NekoId(namespace, path);
    }

    /** Returns the namespace. */
    public String getNamespace() {
        return namespace;
    }

    /** Returns the path. */
    public String getPath() {
        return path;
    }

    /** Returns the {@code "namespace:path"} string form. */
    public String asString() {
        return toString();
    }

    /** Returns the {@code "namespace:path"} string form. */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
