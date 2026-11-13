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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * File implementation of {@link EditorConfigContributor}: it reads the existing configuration
 * through a Gson JsonObject round-trip and only changes keys probe owns, namely the keys under
 * {@code compilerOptions.paths} of jsconfig, the top-level {@code include},
 * {@code compilerOptions.typeRoots}, {@code compilerOptions.typeAcquisition}, the JSX runtime keys
 * of {@code compilerOptions} ({@code jsx}/{@code jsxFactory}/{@code jsxFragmentFactory}/
 * {@code jsxImportSource}), {@code extraPaths} of pyrightconfig, and the contribution keys each
 * backend declares through {@link #mergeVscodeSettings} in {@code .vscode/settings.json}. User
 * and unknown keys are kept. A missing file is created with the defaults. Array keys
 * (include/typeRoots) merge as "refresh the entries probe manages, keep the user's entries"
 * (see {@link #refreshManagedEntries}); they are never overwritten as a whole.
 *
 * <p>Key property: <b>idempotent and repeatable</b>. Every probe merges again, so paths, include
 * and typeRoots always point at the backend's real output directories. This also repairs an
 * existing jsconfig that points at the stale {@code .neko_probe/@package} instead of
 * {@code .neko_probe/typescript/@package}. The JSX runtime keys are likewise corrected to the
 * current value of the engine configuration ({@link ClassFilter#INSTANCE}
 * {@code jsxAutomaticRuntime}): after the user flips that switch, the next probe fixes the
 * existing jsconfig so the IDE does not type-check in the old mode.
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
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "jsconfig 合并失败（" + jsconfigFile + "）：编辑器可能读不到 probe 生成的类型配置", ex);
        }
    }

    @Override
    public void mergeJsConfigIncludes(Path jsconfigFile, List<String> includeGlobs) {
        if (includeGlobs == null || includeGlobs.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            root.add("include", GSON.toJsonTree(refreshManagedEntries(
                    existingStrings(root, "include"), includeGlobs, FileEditorConfigContributor::isProbeManagedInclude)));
            reconcileJsxRuntime(asObject(root, "compilerOptions"));
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "jsconfig include 合并失败（" + jsconfigFile + "）：编辑器可能不扫描 probe 输出目录", ex);
        }
    }

    @Override
    public void mergeJsConfigTypeRoots(Path jsconfigFile, List<String> typeRoots) {
        if (typeRoots == null || typeRoots.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            JsonObject compilerOptions = asObject(root, "compilerOptions");
            compilerOptions.add("typeRoots", GSON.toJsonTree(refreshManagedEntries(
                    existingStrings(compilerOptions, "typeRoots"), typeRoots, FileEditorConfigContributor::isProbeManagedTypeRoot)));
            reconcileJsxRuntime(compilerOptions);
            root.add("compilerOptions", compilerOptions);
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "jsconfig typeRoots 合并失败（" + jsconfigFile + "）", ex);
        }
    }

    @Override
    public void mergeJsConfigTypeAcquisition(Path jsconfigFile, boolean enable) {
        try {
            JsonObject root = readJsonOrEmpty(jsconfigFile);
            JsonObject typeAcquisition = new JsonObject();
            typeAcquisition.addProperty("enable", enable);
            // typeAcquisition is a probe-owned object: replaced as a whole (same semantics as
            // include), other keys kept
            root.add("typeAcquisition", typeAcquisition);
            reconcileJsxRuntime(asObject(root, "compilerOptions"));
            writeJson(jsconfigFile, root);
        } catch (Exception ex) {
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "jsconfig typeAcquisition 合并失败（" + jsconfigFile + "）：ATA 可能重新联网拉 @types", ex);
        }
    }

    @Override
    public void mergePyrightExtraPaths(Path pyrightFile, List<String> extraPaths) {
        if (extraPaths == null || extraPaths.isEmpty()) return;
        try {
            JsonObject root = readJsonOrEmpty(pyrightFile);
            boolean fresh = root.size() == 0;
            if (fresh) {
                // A fresh file gets defaults that give a friendlier completion experience; an existing
                // file keeps the user's typeCheckingMode untouched
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
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "pyrightconfig 合并失败（" + pyrightFile + "）：Pylance 可能不认识 probe 输出目录", ex);
        }
    }

    @Override
    public void mergeVscodePythonExtraPaths(Path settingsFile, List<String> extraPaths) {
        if (extraPaths == null || extraPaths.isEmpty()) return;
        // Backward-compatible entry point: delegates to the generic injection. languageServer is
        // pinned to Pylance only while the user has not chosen explicitly ("Default" falls back
        // to Jedi in some environments, and Jedi does not read pyrightconfig/extraPaths).
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
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "vscode settings 合并失败（" + settingsFile + "）", ex);
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

    /** Recursively merges two JSON objects: leaves of incoming replace or are added, other
     * existing leaves (from the user or other backends) are kept. */
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

    /** Appends the strings of incoming to target, deduplicated by literal value; existing entries,
     * including the user's, are left alone. */
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
                root.add(s.name(), entry); // Snippet names owned by probe are replaced; user snippets are kept
            }
            writeJson(snippetsFile, root);
        } catch (Exception ex) {
            com.tkisor.nekojs.core.error.Diagnostics.report("probe-editor-config", com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "代码片段合并失败（" + snippetsFile + "）", ex);
        }
    }

    // -------------------- Internals --------------------

    /** Standard script file globs for jsconfig include. probe contributes (refreshes) them on
     * every run, and {@link #isProbeManagedInclude} recognises them together with the d.ts globs. */
    private static final Set<String> SCRIPT_FILE_GLOBS = Set.of(
            "./**/*.js", "./**/*.mjs", "./**/*.cjs",
            "./**/*.ts", "./**/*.jsx", "./**/*.tsx");

    /**
     * Refreshes an array key probe owns: drops the old probe-managed entries (including the stale
     * shapes written before), writes the current contribution, and keeps <b>entries the user added
     * as they are</b>, appended after the probe entries and deduplicated. The {@code managed}
     * predicate separates probe entries from user entries; an entry it cannot recognise is kept
     * rather than dropped, and a repeated run deduplicates it.
     */
    private static List<String> refreshManagedEntries(List<String> existing, List<String> contributed,
                                                      java.util.function.Predicate<String> managed) {
        List<String> out = new ArrayList<>(contributed);
        for (String entry : existing) {
            if (managed.test(entry) || out.contains(entry)) continue;
            out.add(entry);
        }
        return out;
    }

    /** probe-managed entry in include: every glob that points into {@code .neko_probe/}, including
     * the stale path shapes written before, plus the standard script globs. */
    private static boolean isProbeManagedInclude(String entry) {
        return entry.contains(".neko_probe/") || SCRIPT_FILE_GLOBS.contains(entry);
    }

    /** probe-managed entry in typeRoots: output directories under {@code .neko_probe/} and the
     * node_modules/@types that probe ships by default. */
    private static boolean isProbeManagedTypeRoot(String entry) {
        return entry.contains(".neko_probe/") || entry.endsWith("node_modules/@types");
    }

    /** Reads the string array under one key of a JSON object; a missing key or a non-array value
     * returns an empty list (other shapes the user writes are not reinterpreted). */
    private static List<String> existingStrings(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        if (el == null || !el.isJsonArray()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            if (e.isJsonPrimitive()) out.add(e.getAsString());
        }
        return out;
    }

    /**
     * Corrects the JSX runtime keys of {@code compilerOptions} to the current value of the engine
     * configuration ({@code jsxAutomaticRuntime} of {@link ClassFilter#INSTANCE}). The engine owns
     * these keys because it performs the JSX transform with the same setting. The strategy matches
     * include/typeRoots: every merge replaces them with the values the engine expects, so a jsconfig
     * stays consistent with runtime compilation after the user flips the switch. Values come from
     * the {@link JSConfigModel} template, so the options always match what {@code WorkspaceGenerator}
     * writes for a newly created jsconfig.
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
            // Corrupt configuration file: rebuild from empty instead of throwing, so one bad edit
            // does not permanently block probe
            return new JsonObject();
        }
    }

    private static void writeJson(Path file, JsonObject root) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root));
    }

    /**
     * Computes the relative path from {@code from} to {@code to} and returns it with POSIX slashes
     * (for relative references in jsconfig/pyrightconfig). Both sides are first brought to their
     * canonical form (existing path: toRealPath; missing path: the joined form). On Windows, mixing
     * an 8.3 short name ({@code RUNNER~1}) with the long toRealPath name otherwise makes
     * relativize climb to the drive root and produce a garbage absolute path.
     */
    public static String relativePosix(Path from, Path to) {
        return NekoJSPaths.canonicalForm(from).relativize(NekoJSPaths.canonicalForm(to))
                .toString().replace('\\', '/');
    }

    /** Convenience: the relative POSIX path from {@link NekoJSPaths#root()} to the given directory. */
    public static String relativeFromRoot(NekoJSPaths paths, Path to) {
        return relativePosix(paths.root(), to);
    }
}
