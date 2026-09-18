package com.tkisor.nekojs.core.module;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Shared canonical identity for module cache keys and trust boundaries. */
final class NekoCanonicalPath {
    private NekoCanonicalPath() {}

    static String of(Path path) {
        Objects.requireNonNull(path, "path");
        Path canonical = path.normalize().toAbsolutePath();
        try {
            canonical = canonical.toRealPath();
        } catch (IOException ignored) {
            // A missing source still needs a deterministic lexical identity.
        }
        String normalized = canonical.toString().replace('\\', '/');
        return isWindows() ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    static boolean isWithin(String subject, String root) {
        if (subject == null || root == null) {
            return false;
        }
        String prefix = root.endsWith("/") ? root : root + "/";
        return subject.equals(root) || subject.startsWith(prefix);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
