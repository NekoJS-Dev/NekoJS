package com.tkisor.nekojs.api.ui;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A controlled UI resource identifier ({@code namespace:path}). Follows the same
 * grammar and safety rules as the ticket 29 asset ids written by
 * {@code AssetGeneratorJS}: lowercase namespace and path, no {@code ..} segments,
 * at most one {@code :}. A missing namespace defaults to {@code minecraft}.
 *
 * <p>Ids are the only way images, icons, and fonts enter the UI contract; file
 * paths and URLs are not accepted.
 */
public record UiResourceId(String namespace, String path) {
    /** Namespace grammar shared with vanilla resource locations: [a-z0-9_.-]. */
    static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    /** Path grammar shared with vanilla resource locations: [a-z0-9/._-]. */
    static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");

    public UiResourceId {
        if (!VALID_NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace '" + namespace + "'");
        }
        if (!VALID_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid resource path '" + path + "'");
        }
    }

    /**
     * Parses a resource id string.
     *
     * @param id raw id, for example {@code mymod:gui/panel}
     * @return the id, or empty when the string violates the controlled grammar
     */
    public static Optional<UiResourceId> parse(String id) {
        if (id == null) return Optional.empty();
        int separator = id.indexOf(':');
        if (id.indexOf(':', separator + 1) >= 0) return Optional.empty();
        String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
        String path = separator < 0 ? id : id.substring(separator + 1);
        if (!VALID_NAMESPACE.matcher(namespace).matches() || !VALID_PATH.matcher(path).matches()) {
            return Optional.empty();
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return Optional.empty();
        }
        return Optional.of(new UiResourceId(namespace, path));
    }

    /** The canonical {@code namespace:path} form. */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
