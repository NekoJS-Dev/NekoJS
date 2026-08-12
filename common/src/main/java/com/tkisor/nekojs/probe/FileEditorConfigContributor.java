package com.tkisor.nekojs.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.Snippet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link EditorConfigContributor} 的文件实现：用 Gson JsonObject round-trip 读取既有配置，
 * 仅修改 probe 拥有的键（jsconfig 的 {@code compilerOptions.paths} 对应键、顶层 {@code include}、
 * {@code compilerOptions.typeRoots}，以及 pyrightconfig 的 {@code extraPaths}），保留用户自定义键
 * 与未知键。文件不存在则按默认创建。
 *
 * <p>关键属性：**幂等且可重复**——每次 probe 都重新合并，保证 paths/include/typeRoots 始终指向
 * backend 真实的输出目录（修复既有 jsconfig 指向旧 {@code .neko_probe/@package} 而非
 * {@code .neko_probe/typescript/@package} 的 stale 问题）。
 */
public final class FileEditorConfigContributor implements EditorConfigContributor {

    private static final com.google.gson.Gson GSON =
            new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    @Override
    public void mergeJsConfigPaths(Path jsconfigFile, Map<String, List<String>> pathAliases) {
        if (pathAliases == null || pathAliases.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            JsonObject compilerOptions = asObject(root, "compilerOptions");
            JsonObject paths = compilerOptions.has("paths") ? asObject(compilerOptions, "paths") : new JsonObject();
            for (var e : pathAliases.entrySet()) {
                paths.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            compilerOptions.add("paths", paths);
            root.add("compilerOptions", compilerOptions);
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: jsconfig merge failed at {}", jsconfigFile, ex);
        }
    }

    @Override
    public void mergeJsConfigIncludes(Path jsconfigFile, List<String> includeGlobs) {
        if (includeGlobs == null || includeGlobs.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            // include 是 probe 拥有的数组：整体替换（用户若自定义大概率就是要覆盖），其余键保留
            root.add("include", GSON.toJsonTree(includeGlobs));
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: jsconfig include merge failed at {}", jsconfigFile, ex);
        }
    }

    @Override
    public void mergeJsConfigTypeRoots(Path jsconfigFile, List<String> typeRoots) {
        if (typeRoots == null || typeRoots.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            JsonObject compilerOptions = asObject(root, "compilerOptions");
            // typeRoots 是 probe 拥有的数组：整体替换（语义同 include），其余键保留
            compilerOptions.add("typeRoots", GSON.toJsonTree(typeRoots));
            root.add("compilerOptions", compilerOptions);
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: jsconfig typeRoots merge failed at {}", jsconfigFile, ex);
        }
    }

    @Override
    public void mergePyrightExtraPaths(Path pyrightFile, List<String> extraPaths) {
        if (extraPaths == null || extraPaths.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(pyrightFile);
            boolean fresh = root.size() == 0;
            if (fresh) {
                // fresh 文件附带你补全体验更友好的默认；既有文件不动用户的 typeCheckingMode
                root.addProperty("typeCheckingMode", "basic");
                root.addProperty("reportMissingModuleSource", "none");
            }
            JsonArray arr = root.has("extraPaths") && root.get("extraPaths").isJsonArray()
                    ? root.getAsJsonArray("extraPaths") : new JsonArray();
            Set<String> existing = new LinkedHashSet<>();
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) existing.add(el.getAsString());
            }
            for (String p : extraPaths) {
                if (existing.add(p)) arr.add(p);
            }
            root.add("extraPaths", arr);
            writeJson(pyrightFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: pyrightconfig merge failed at {}", pyrightFile, ex);
        }
    }

    @Override
    public void mergeVscodeSnippets(Path snippetsFile, List<Snippet> snippets) {
        if (snippets == null || snippets.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(snippetsFile);
            for (Snippet s : snippets) {
                JsonObject entry = new JsonObject();
                entry.addProperty("prefix", s.prefix());
                JsonArray body = new JsonArray();
                for (String line : s.body().split("\n", -1)) body.add(line);
                entry.add("body", body);
                if (s.description() != null) entry.addProperty("description", s.description());
                root.add(s.name(), entry); // probe 拥有的片段名替换，用户片段保留
            }
            writeJson(snippetsFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: snippets merge failed at {}", snippetsFile, ex);
        }
    }

    // -------------------- 内部 --------------------

    private static JsonObject asObject(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        if (el != null && el.isJsonObject()) return el.getAsJsonObject();
        JsonObject obj = new JsonObject();
        parent.add(key, obj);
        return obj;
    }

    private static JsonObject readJsonOrEmpty(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement el = JsonParser.parseReader(reader);
            return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            // 损坏的配置文件：从空开始重建（不抛，避免一次手误的编辑永久阻断 probe）
            return new JsonObject();
        }
    }

    private static void writeJson(Path file, JsonObject root) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root));
    }

    /**
     * 计算 {@code from} 到 {@code to} 的相对路径，以 POSIX 斜杠返回（用于 jsconfig/pyrightconfig 中的相对引用）。
     */
    public static String relativePosix(Path from, Path to) {
        return from.relativize(to).toString().replace('\\', '/');
    }

    /** 便捷：{@link NekoJSPaths#root()} 到指定目录的相对 POSIX 路径。 */
    public static String relativeFromRoot(NekoJSPaths paths, Path to) {
        return relativePosix(paths.root(), to);
    }
}
