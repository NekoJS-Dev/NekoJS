package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoEsmVirtualModuleRegistry {
    private static final Path ROOT = NekoJSPaths.get().root().resolve(".native_esm_modules").normalize().toAbsolutePath();
    private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_PATHS = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_PATHS_BY_FILE_NAME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> GENERATIONS = new ConcurrentHashMap<>();
    /** key（path 字符串，与 SOURCES 同键）→ 所属 ScriptType；跨类型共享模块（node:/java:/裸包名）为 null。 */
    private static final Map<String, ScriptType> TYPES = new ConcurrentHashMap<>();

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
        ScriptType type = scriptTypeOf(moduleId);
        if (type != null) {
            TYPES.put(key, type);
        }
        return path.toUri();
    }

    public static void reserve(String moduleId) {
        Path path = path(moduleId);
        String key = path.toString();
        String displayPath = displayPathForModuleId(moduleId);
        SOURCES.putIfAbsent(key, "");
        DISPLAY_PATHS.putIfAbsent(key, displayPath);
        DISPLAY_PATHS_BY_FILE_NAME.putIfAbsent(path.getFileName().toString(), displayPath);
        ScriptType type = scriptTypeOf(moduleId);
        if (type != null) {
            TYPES.putIfAbsent(key, type);
        }
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
        int query = fileName.indexOf('?');
        if (query >= 0) {
            fileName = fileName.substring(0, query);
        }
        int fragment = fileName.indexOf('#');
        if (fragment >= 0) {
            fileName = fileName.substring(0, fragment);
        }
        return DISPLAY_PATHS_BY_FILE_NAME.get(fileName);
    }

    public static void invalidate(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return;
        Path path = path(moduleId);
        String key = path.toString();
        SOURCES.remove(key);
        DISPLAY_PATHS.remove(key);
        DISPLAY_PATHS_BY_FILE_NAME.remove(path.getFileName().toString());
        TYPES.remove(key);
        GENERATIONS.merge(moduleId, 1, Integer::sum);
    }

    public static void clear() {
        SOURCES.clear();
        DISPLAY_PATHS.clear();
        DISPLAY_PATHS_BY_FILE_NAME.clear();
        GENERATIONS.clear();
        TYPES.clear();
    }

    /**
     * 仅清空指定 {@link ScriptType} 的虚拟 ESM 模块（含对应 generation 计数）。
     * moduleId 内嵌 nekojs root 相对路径（如 {@code server_scripts/foo.mjs}，可带
     * {@code #cjs-interop}、{@code #dynamic}、{@code #namespace-capture:...} 合成后缀），
     * 据此推导所属类型；{@code node:}、{@code java:}、裸包名等跨类型共享模块不受影响。
     * 避免单机单类型 reload 误清其它类型已解析的虚拟 URI（重新生成哈希路径）。
     */
    public static void clear(ScriptType type) {
        if (type == null) {
            clear();
            return;
        }
        List<String> keys = new ArrayList<>();
        TYPES.forEach((key, entryType) -> {
            if (entryType == type) {
                keys.add(key);
            }
        });
        for (String key : keys) {
            SOURCES.remove(key);
            DISPLAY_PATHS.remove(key);
            DISPLAY_PATHS_BY_FILE_NAME.remove(Path.of(key).getFileName().toString());
            TYPES.remove(key);
        }
        GENERATIONS.keySet().removeIf(moduleId -> scriptTypeOf(moduleId) == type);
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
        if (normalized.startsWith("java:") || normalized.startsWith("java.") || normalized.startsWith("java/") || normalized.startsWith("node:")) {
            return normalized;
        }
        try {
            Path parsed = Path.of(normalized);
            Path path = parsed.isAbsolute() ? parsed.normalize().toAbsolutePath() : NekoJSPaths.get().root().resolve(parsed).normalize().toAbsolutePath();
            return NekoJSPaths.get().root().relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) { // path resolution fails → return raw normalized string
            return normalized;
        }
    }

    /**
     * 从 moduleId 推导所属 {@link ScriptType}：moduleId 通常是 nekojs root 相对路径
     * （如 {@code server_scripts/foo.mjs}），也可能带 {@code #cjs-interop}、{@code #dynamic}、
     * {@code #namespace-capture:...} 等合成后缀；{@code node:}、{@code java:}、裸包名等
     * 非脚本路径 moduleId 为跨类型共享模块，返回 null。
     */
    private static ScriptType scriptTypeOf(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return null;
        }
        String base = moduleId;
        int hash = base.indexOf('#');
        if (hash >= 0) {
            base = base.substring(0, hash);
        }
        base = base.replace('\\', '/');
        int slash = base.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String first = base.substring(0, slash);
        for (ScriptType type : ScriptType.all()) {
            Path typePath = type.path;
            String dirName = typePath == null ? type.name + "_scripts" : typePath.getFileName().toString();
            // Windows 大小写不敏感：Server_scripts 与 server_scripts 指向同一类型目录，
            // 忽略大小写匹配，防止手建异大小写目录下的模块被误判为跨类型共享、逃脱按类型清理
            if (first.equalsIgnoreCase(dirName)) {
                return type;
            }
        }
        return null;
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
