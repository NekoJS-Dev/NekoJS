package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.NekoId;

/**
 * Resource identifier (ID) facade, exposed to scripts as the global {@code ID}.
 *
 * <p>It converts between strings and {@link NekoId}. Scripts usually pass {@code "minecraft:stone"}
 * or omit the namespace ({@code "stone"}), in which case the default namespace
 * {@link NekoId#DEFAULT_NAMESPACE} is used.
 */
public interface IdFacade {
    /**
     * Parses a string into a {@link NekoId}; a value without a namespace prefix uses the default
     * namespace {@link NekoId#DEFAULT_NAMESPACE}.
     *
     * @throws IllegalArgumentException when the namespace or path part is blank
     */
    NekoId of(String value);

    /**
     * Builds a {@link NekoId} from an explicit namespace and path.
     *
     * @throws IllegalArgumentException when {@code namespace} or {@code path} is blank
     */
    NekoId of(String namespace, String path);

    /** Parses a strict resource ID; unqualified values use {@code nekojs}. */
    default NekoId resource(String value) {
        return NekoId.resource(value);
    }

    /** Constructs a strict resource ID without trimming or changing case. */
    default NekoId resource(String namespace, String path) {
        return NekoId.resource(namespace, path);
    }

    /** Returns the namespace of a {@link NekoId}. */
    String namespace(NekoId id);

    /** Returns the path of a {@link NekoId}. */
    String path(NekoId id);

    /** Returns a {@link NekoId} as a {@code "namespace:path"} string. */
    String asString(NekoId id);
}
