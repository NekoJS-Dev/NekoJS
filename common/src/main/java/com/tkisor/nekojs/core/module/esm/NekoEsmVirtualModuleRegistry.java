package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoEsmVirtualModuleRegistry {
    private static final Path ROOT = NekoJSPaths.ROOT.resolve(".native_esm_modules").normalize().toAbsolutePath();
    private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_PATHS = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_PATHS_BY_FILE_NAME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> GENERATIONS = new ConcurrentHashMap<>();

    private NekoEsmVirtualModuleRegistry() {}

    public static URI uri(String moduleId) {
        return path(moduleId).toUri();
    }

    public static URI register(String moduleId, String source) {
        Path path = path(moduleId);
        String key = path.toString();
        String displayPath = displayPathForModuleId(moduleId);
        SOURCES.put(key, source == null ? "" : source);
        DISPLAY_PATHS.put(key, displayPath);
        DISPLAY_PATHS_BY_FILE_NAME.put(path.getFileName().toString(), displayPath);
        return path.toUri();
    }

    public static void reserve(String moduleId) {
        Path path = path(moduleId);
        String key = path.toString();
        String displayPath = displayPathForModuleId(moduleId);
        SOURCES.putIfAbsent(key, "");
        DISPLAY_PATHS.putIfAbsent(key, displayPath);
        DISPLAY_PATHS_BY_FILE_NAME.putIfAbsent(path.getFileName().toString(), displayPath);
    }

    public static boolean isVirtualModule(Path path) {
        return source(path) != null;
    }

    public static boolean isVirtualDirectory(Path path) {
        return path != null && path.normalize().toAbsolutePath().equals(ROOT);
    }

    public static boolean isVirtualPath(Path path) {
        return path != null && path.normalize().toAbsolutePath().startsWith(ROOT);
    }

    public static String source(Path path) {
        if (path == null) {
            return null;
        }
        return SOURCES.get(path.normalize().toAbsolutePath().toString());
    }

    public static String displayPath(Path path) {
        if (path == null) {
            return null;
        }
        String displayPath = DISPLAY_PATHS.get(path.normalize().toAbsolutePath().toString());
        if (displayPath != null) {
            return displayPath;
        }
        Path fileName = path.getFileName();
        return fileName == null ? null : DISPLAY_PATHS_BY_FILE_NAME.get(fileName.toString());
    }

    public static String displayPath(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }
        String normalized = pathOrUri.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return DISPLAY_PATHS_BY_FILE_NAME.get(fileName);
    }

    public static void invalidate(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return;
        Path path = path(moduleId);
        String key = path.toString();
        SOURCES.remove(key);
        DISPLAY_PATHS.remove(key);
        DISPLAY_PATHS_BY_FILE_NAME.remove(path.getFileName().toString());
        GENERATIONS.merge(moduleId, 1, Integer::sum);
    }

    public static void clear() {
        SOURCES.clear();
        DISPLAY_PATHS.clear();
        DISPLAY_PATHS_BY_FILE_NAME.clear();
        GENERATIONS.clear();
    }

    public static Path root() {
        return ROOT;
    }

    private static Path path(String moduleId) {
        return ROOT.resolve(stableKey(versionedModuleId(moduleId)) + ".mjs").normalize().toAbsolutePath();
    }

    private static String versionedModuleId(String moduleId) {
        return (moduleId == null ? "module" : moduleId) + "#v" + GENERATIONS.getOrDefault(moduleId, 0);
    }

    private static String displayPathForModuleId(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return "<native-esm>";
        }
        String normalized = moduleId.replace('\\', '/');
        if (normalized.startsWith("java:") || normalized.startsWith("node:")) {
            return normalized;
        }
        try {
            Path parsed = Path.of(normalized);
            Path path = parsed.isAbsolute() ? parsed.normalize().toAbsolutePath() : NekoJSPaths.ROOT.resolve(parsed).normalize().toAbsolutePath();
            return NekoJSPaths.ROOT.relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private static String stableKey(String moduleId) {
        String value = moduleId == null ? "module" : moduleId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
