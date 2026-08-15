package com.tkisor.nekojs.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.JSConfigModel;
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
 * {@code compilerOptions.typeRoots}、{@code compilerOptions} 的 JSX 运行时键
 * （{@code jsx}/{@code jsxFactory}/{@code jsxFragmentFactory}/{@code jsxImportSource}），
 * pyrightconfig 的 {@code extraPaths}，以及 {@code .vscode/settings.json} 中各 backend 通过
 * {@link #mergeVscodeSettings} 声明的贡献键），保留用户自定义键与未知键。文件不存在则按默认创建。
 *
 * <p>关键属性：**幂等且可重复**——每次 probe 都重新合并，保证 paths/include/typeRoots 始终指向
 * backend 真实的输出目录（修复既有 jsconfig 指向旧 {@code .neko_probe/@package} 而非
 * {@code .neko_probe/typescript/@package} 的 stale 问题）；JSX 运行时键同理始终校正到引擎配置
 * （{@link ClassFilter#INSTANCE} 的 {@code jsxAutomaticRuntime}）的当前值——用户翻转该开关后，
 * 既有 jsconfig 在下一次 probe 被纠正，避免 IDE 按旧模式做类型检查。
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
            reconcileJsxRuntime(compilerOptions);
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
            reconcileJsxRuntime(asObject(root, "compilerOptions"));
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
            reconcileJsxRuntime(compilerOptions);
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
    public void mergeVscodePythonExtraPaths(Path settingsFile, List<String> extraPaths) {
        if (extraPaths == null || extraPaths.isEmpty()) return;
        // 向后兼容入口：委托给通用注入机制。languageServer 仅在用户未显式选择时固定为 Pylance
        // （"Default" 在部分环境下会退回 Jedi，而 Jedi 不读 pyrightconfig/extraPaths）。
        mergeVscodeSettings(settingsFile, List.of(
                new VscodeSetting("python.languageServer", "Pylance", VscodeSettingMerge.SET_IF_ABSENT),
                new VscodeSetting("python.analysis.extraPaths", extraPaths, VscodeSettingMerge.EXTEND_STRING_ARRAY)));
    }

    @Override
    public void mergeVscodeSettings(Path settingsFile, List<VscodeSetting> settings) {
        if (settings == null || settings.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(settingsFile);
            boolean changed = false;
            for (VscodeSetting setting : settings) {
                changed |= applyVscodeSetting(root, setting);
            }
            if (changed) writeJson(settingsFile, root);
        } catch (Exception ex) {
            NekoJS.LOGGER.debug("EditorConfig: vscode settings merge failed at {}", settingsFile, ex);
        }
    }

    private static boolean applyVscodeSetting(JsonObject root, VscodeSetting setting) {
        String[] parts = setting.key().split("\\.");
        JsonObject cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = cursor.get(parts[i]);
            if (next != null && next.isJsonObject()) {
                cursor = next.getAsJsonObject();
            } else {
                JsonObject child = new JsonObject();
                cursor.add(parts[i], child);
                cursor = child;
            }
        }
        String leaf = parts[parts.length - 1];
        JsonElement value = GSON.toJsonTree(setting.value());
        return switch (setting.mode()) {
            case SET -> {
                if (value.equals(cursor.get(leaf))) {
                    yield false;
                }
                cursor.add(leaf, value);
                yield true;
            }
            case SET_IF_ABSENT -> {
                if (cursor.has(leaf)) {
                    yield false;
                }
                cursor.add(leaf, value);
                yield true;
            }
            case MERGE_OBJECT -> {
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("MERGE_OBJECT contribution must be an object: " + setting.key());
                }
                JsonElement existing = cursor.get(leaf);
                if (existing != null && existing.isJsonObject()) {
                    yield mergeObjects(existing.getAsJsonObject(), value.getAsJsonObject());
                }
                cursor.add(leaf, value);
                yield true;
            }
            case EXTEND_STRING_ARRAY -> {
                if (!value.isJsonArray()) {
                    throw new IllegalArgumentException(
                            "EXTEND_STRING_ARRAY contribution must be an array: " + setting.key());
                }
                JsonElement existing = cursor.get(leaf);
                if (existing == null || !existing.isJsonArray()) {
                    cursor.add(leaf, value);
                    yield true;
                }
                yield appendUniqueStrings(existing.getAsJsonArray(), value.getAsJsonArray());
            }
        };
    }

    /** 递归合并两个 JSON 对象：incoming 的叶子替换/新增，既有其它叶子（用户或其它 backend 的贡献）保留。 */
    private static boolean mergeObjects(JsonObject target, JsonObject incoming) {
        boolean changed = false;
        for (var e : incoming.entrySet()) {
            JsonElement inc = e.getValue();
            JsonElement cur = target.get(e.getKey());
            if (inc.isJsonObject() && cur != null && cur.isJsonObject()) {
                changed |= mergeObjects(cur.getAsJsonObject(), inc.getAsJsonObject());
            } else if (!inc.equals(cur)) {
                target.add(e.getKey(), inc);
                changed = true;
            }
        }
        return changed;
    }

    /** 把 incoming 的字符串按字面值去重追加到 target；已有条目（含用户条目）不动。 */
    private static boolean appendUniqueStrings(JsonArray target, JsonArray incoming) {
        Set<String> existing = new LinkedHashSet<>();
        for (JsonElement el : target) {
            if (el.isJsonPrimitive()) existing.add(el.getAsString());
        }
        boolean changed = false;
        for (JsonElement el : incoming) {
            if (!el.isJsonPrimitive() || !existing.add(el.getAsString())) continue;
            target.add(el);
            changed = true;
        }
        return changed;
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

    /**
     * 把 {@code compilerOptions} 的 JSX 运行时键校正到引擎配置（{@link ClassFilter#INSTANCE} 的
     * {@code jsxAutomaticRuntime}）的当前值。这些键由引擎拥有（运行时按同一配置做 JSX 变换），
     * 策略与 include/typeRoots 相同：每次合并整体替换为引擎期望值，避免用户翻转开关后
     * jsconfig 与运行时编译行为不一致。取值直接来自 {@link JSConfigModel} 模板，
     * 保证与 {@code WorkspaceGenerator} 新建的 jsconfig 永远写出一致的选项。
     */
    private static void reconcileJsxRuntime(JsonObject compilerOptions) {
        JSConfigModel template = new JSConfigModel();
        if (ClassFilter.INSTANCE.config().jsxAutomaticRuntime()) {
            template.useAutomaticJsxRuntime();
        }
        JSConfigModel.CompilerOptions opts = template.compilerOptions;
        compilerOptions.addProperty("jsx", opts.jsx);
        putOrRemove(compilerOptions, "jsxFactory", opts.jsxFactory);
        putOrRemove(compilerOptions, "jsxFragmentFactory", opts.jsxFragmentFactory);
        putOrRemove(compilerOptions, "jsxImportSource", opts.jsxImportSource);
    }

    private static void putOrRemove(JsonObject obj, String key, String value) {
        if (value == null) obj.remove(key);
        else obj.addProperty(key, value);
    }

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
