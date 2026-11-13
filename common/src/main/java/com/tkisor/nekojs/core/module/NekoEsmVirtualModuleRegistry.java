package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoEsmVirtualModuleRegistry implements NekoVirtualModuleView {
    private final Path root;
    private final Map<String, String> sources = new ConcurrentHashMap<>();
    private final Map<String, String> displayPaths = new ConcurrentHashMap<>();
    private final Map<String, String> displayPathsByFileName = new ConcurrentHashMap<>();
    /** Maps a filename hash of an .mjs file to the current module key for that entry. */
    private final Map<String, String> keyByFileName = new ConcurrentHashMap<>();
    private final Map<String, Integer> generations = new ConcurrentHashMap<>();
    /** Maps a path key (consistent with SOURCES) to its {@link ScriptType}; returns null for shared modules (node:, java:, or bare package names). */
    private final Map<String, ScriptType> types = new ConcurrentHashMap<>();

    public NekoEsmVirtualModuleRegistry(Path gameRoot) {
        Path canonicalGameRoot = gameRoot.normalize().toAbsolutePath();
        try {
            canonicalGameRoot = canonicalGameRoot.toRealPath();
        } catch (IOException ignored) {
            // A not-yet-created root still has a stable lexical identity.
        }
        this.root = canonicalGameRoot.resolve(".native_esm_modules").normalize().toAbsolutePath();
    }

    public URI uri(String moduleId) {
        return path(moduleId).toUri();
    }

    public URI register(String moduleId, String source) {
        Path path = path(moduleId);
        String key = path.toString();
        String displayPath = displayPathForModuleId(moduleId);
        sources.put(key, source == null ? "" : source);
        displayPaths.put(key, displayPath);
        putFileNameEntry(key, displayPath);
        ScriptType type = scriptTypeOf(moduleId);
        if (type != null) {
            types.put(key, type);
        }
        return path.toUri();
    }

    public void reserve(String moduleId) {
        Path path = path(moduleId);
        String key = path.toString();
        String displayPath = displayPathForModuleId(moduleId);
        sources.putIfAbsent(key, "");
        displayPaths.putIfAbsent(key, displayPath);
        putFileNameEntryIfAbsent(key, displayPath);
        ScriptType type = scriptTypeOf(moduleId);
        if (type != null) {
            types.putIfAbsent(key, type);
        }
    }

    public boolean isVirtualModule(Path path) {
        return source(path) != null;
    }

    public boolean isVirtualDirectory(Path path) {
        return path != null && path.normalize().toAbsolutePath().equals(root);
    }

    public boolean isVirtualPath(Path path) {
        return path != null && path.normalize().toAbsolutePath().startsWith(root);
    }

    public String source(Path path) {
        if (path == null) {
            return null;
        }
        return sources.get(path.normalize().toAbsolutePath().toString());
    }

    public String displayPath(Path path) {
        if (path == null) {
            return null;
        }
        String displayPath = displayPaths.get(path.normalize().toAbsolutePath().toString());
        if (displayPath != null) {
            return displayPath;
        }
        Path fileName = path.getFileName();
        return fileName == null ? null : displayPathsByFileName.get(fileName.toString());
    }

    public String displayPath(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }
        String normalized = pathOrUri.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int query = fileName.indexOf('?');
        if (query >= 0) {
            fileName = fileName.substring(0, query);
        }
        int fragment = fileName.indexOf('#');
        if (fragment >= 0) {
            fileName = fileName.substring(0, fragment);
        }
        return displayPathsByFileName.get(fileName);
    }

    public void invalidate(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return;
        Path path = path(moduleId);
        String key = path.toString();
        sources.remove(key);
        displayPaths.remove(key);
        removeFileNameEntry(key);
        types.remove(key);
        generations.merge(moduleId, 1, Integer::sum);
    }

    public void clear() {
        sources.clear();
        displayPaths.clear();
        displayPathsByFileName.clear();
        keyByFileName.clear();
        generations.clear();
        types.clear();
    }

    /**
     * Clears virtual ESM modules for a specific {@link ScriptType} (including generation count).
     * The moduleId embeds a path relative to the nekojs root (e.g., {@code server_scripts/foo.mjs},
     * potentially with suffixes like {@code #cjs-interop}, {@code #dynamic}, or
     * {@code #namespace-capture:...}). Cross-type shared modules ({@code node:}, {@code java:}, bare package names)
     * are unaffected. This avoids accidentally clearing virtual URIs resolved for other types
     * when reloading a single type.
     */
    public void clear(ScriptType type) {
        if (type == null) {
            clear();
            return;
        }
        List<String> keys = new ArrayList<>();
        types.forEach((key, entryType) -> {
            if (entryType == type) {
                keys.add(key);
            }
        });
        for (String key : keys) {
            sources.remove(key);
            displayPaths.remove(key);
            removeFileNameEntry(key);
            types.remove(key);
        }
        generations.keySet().removeIf(moduleId -> scriptTypeOf(moduleId) == type);
    }

    public Path root() {
        return root;
    }

    private void putFileNameEntry(String key, String displayPath) {
        String fileName = fileNameOfKey(key);
        if (fileName == null) {
            return;
        }
        displayPathsByFileName.put(fileName, displayPath);
        keyByFileName.put(fileName, key);
    }

    private void putFileNameEntryIfAbsent(String key, String displayPath) {
        String fileName = fileNameOfKey(key);
        if (fileName == null) {
            return;
        }
        displayPathsByFileName.putIfAbsent(fileName, displayPath);
        keyByFileName.putIfAbsent(fileName, key);
    }

    private void removeFileNameEntry(String key) {
        String fileName = fileNameOfKey(key);
        if (fileName == null) {
            return;
        }
        if (key.equals(keyByFileName.get(fileName))) {
            displayPathsByFileName.remove(fileName);
            keyByFileName.remove(fileName);
        }
    }

    private static String fileNameOfKey(String key) {
        Path fileName = Path.of(key).getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private Path path(String moduleId) {
        return root.resolve(stableKey(versionedModuleId(moduleId)) + ".mjs").normalize().toAbsolutePath();
    }

    private String versionedModuleId(String moduleId) {
        return (moduleId == null ? "module" : moduleId) + "#v" + generations.getOrDefault(moduleId, 0);
    }

    private String displayPathForModuleId(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return "<native-esm>";
        }
        String normalized = moduleId.replace('\\', '/');
        if (isSharedVirtualModule(normalized)) {
            return normalized;
        }
        try {
            Path parsed = root.getFileSystem().getPath(normalized);
            Path path = parsed.isAbsolute() ? parsed.normalize().toAbsolutePath() : root.getParent().resolve(parsed).normalize().toAbsolutePath();
            return root.getParent().relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) { // path resolution fails → return raw normalized string
            return normalized;
        }
    }

    /**
     * Derives the {@link ScriptType} from a moduleId. The moduleId is usually a path relative to the
     * nekojs root, potentially including suffixes like {@code #cjs-interop}, {@code #dynamic}, or
     * {@code #namespace-capture:...}. Non-script paths (e.g., {@code node:}, {@code java:}, bare package names)
     * are considered cross-type shared modules and return null.
     */
    private ScriptType scriptTypeOf(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return null;
        }
        String base = moduleId;
        int hash = base.indexOf('#');
        if (hash >= 0) {
            base = base.substring(0, hash);
        }
        base = base.replace('\\', '/');
        if (isSharedVirtualModule(base)) {
            return null;
        }
        int slash = base.indexOf('/');
        if (slash < 0) {
            return null;
        }
        Path parsed = root.getFileSystem().getPath(base);
        Path resolved = parsed.isAbsolute() ? parsed : root.getParent().resolve(parsed);
        return ScriptPathProvider.typeOf(resolved);
    }

    /** Recognizes shared Java and Node names before filesystem-based ownership lookup. */
    private static boolean isSharedVirtualModule(String moduleId) {
        return moduleId.startsWith("java:") || moduleId.startsWith("java.")
                || moduleId.startsWith("java/") || moduleId.startsWith("node:");
    }

    private static String stableKey(String moduleId) {
        String value = moduleId == null ? "module" : moduleId;
        return NekoModuleHash.sha256(value).substring(0, 32);
    }
}
