package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModuleHash;
import com.tkisor.nekojs.core.module.NekoVirtualModuleView;

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
    /** fileName（哈希 .mjs 文件名）→ 当前拥有该 file-name 条目的模块 key。 */
    private final Map<String, String> keyByFileName = new ConcurrentHashMap<>();
    private final Map<String, Integer> generations = new ConcurrentHashMap<>();
    /** key（path 字符串，与 SOURCES 同键）→ 所属 ScriptType；跨类型共享模块（node:/java:/裸包名）为 null。 */
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

    public NekoEsmVirtualModuleRegistry() {
        this(NekoJSPaths.get().root());
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
     * 仅清空指定 {@link ScriptType} 的虚拟 ESM 模块（含对应 generation 计数）。
     * moduleId 内嵌 nekojs root 相对路径（如 {@code server_scripts/foo.mjs}，可带
     * {@code #cjs-interop}、{@code #dynamic}、{@code #namespace-capture:...} 合成后缀），
     * 据此推导所属类型；{@code node:}、{@code java:}、裸包名等跨类型共享模块不受影响。
     * 避免单机单类型 reload 误清其它类型已解析的虚拟 URI（重新生成哈希路径）。
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
        if (normalized.startsWith("java:") || normalized.startsWith("java.") || normalized.startsWith("java/") || normalized.startsWith("node:")) {
            return normalized;
        }
        try {
            Path parsed = Path.of(normalized);
            Path path = parsed.isAbsolute() ? parsed.normalize().toAbsolutePath() : root.getParent().resolve(parsed).normalize().toAbsolutePath();
            return root.getParent().relativize(path).toString().replace('\\', '/');
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
        int slash = base.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String first = base.substring(0, slash);
        for (ScriptType type : ScriptType.all()) {
            Path typePath = ScriptTypeEnv.scriptsDir(type);
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
        return NekoModuleHash.sha256(value).substring(0, 32);
    }
}
