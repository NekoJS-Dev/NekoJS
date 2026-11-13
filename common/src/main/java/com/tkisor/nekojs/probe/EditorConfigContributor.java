package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.events.Snippet;

import java.util.List;
import java.util.Map;

/**
 * Editor configuration contribution point: each {@link ProbeBackend} contributes in
 * {@link ProbeBackend#contributeEditorConfig} the configuration fragments its language tool
 * needs, and the implementation merges them into the corresponding editor configuration files.
 *
 * <p>Separates what a backend declares from where and how the configuration is merged:
 * <ul>
 *   <li>{@link #mergeJsConfigPaths}: merges TS path aliases (e.g. {@code java:*},
 *       {@code @side-only/*}, {@code @special/*}) into a script directory's {@code jsconfig.json}
 *       (probe-owned keys are replaced, user-defined keys kept).</li>
 *   <li>{@link #mergeJsConfigIncludes} / {@link #mergeJsConfigTypeRoots}: merges include and
 *       typeRoots into the same jsconfig.json (the arrays are replaced as a whole with the probe
 *       contribution).</li>
 *   <li>{@link #mergePyrightExtraPaths}: merges paths into {@code extraPaths} of
 *       {@code pyrightconfig.json} (deduplicated).</li>
 * </ul>
 *
 * <p>An interface instead of direct file writes makes this testable: a test can pass a recording
 * implementation and assert what a backend contributed without touching the disk.
 */
public interface EditorConfigContributor {

    /**
     * Merges path aliases into {@code compilerOptions.paths} of the given {@code jsconfig.json}.
     *
     * @param jsconfigFile target jsconfig.json, created if missing
     * @param pathAliases  alias to candidate path list; probe-owned keys replace the old value,
     *                     user-defined keys are kept
     */
    void mergeJsConfigPaths(java.nio.file.Path jsconfigFile, Map<String, List<String>> pathAliases);

    /**
     * Merges include globs into the top-level {@code include} array of the given
     * {@code jsconfig.json}.
     *
     * <p>{@code include} is a probe-owned array: it is replaced as a whole with the contribution.
     * A user who customizes include almost certainly wants to override it, so that is acceptable.
     * The file is created if missing; other keys are kept.
     *
     * @param jsconfigFile target jsconfig.json, created if missing
     * @param includeGlobs include globs relative to the script directory (the probe contribution)
     */
    void mergeJsConfigIncludes(java.nio.file.Path jsconfigFile, List<String> includeGlobs);

    /**
     * Merges typeRoots into the {@code compilerOptions.typeRoots} array of the given
     * {@code jsconfig.json}.
     *
     * <p>{@code typeRoots} is a probe-owned array: it is replaced as a whole with the
     * contribution, with the same semantics as {@link #mergeJsConfigIncludes}. The file is created
     * if missing; other keys are kept.
     *
     * @param jsconfigFile target jsconfig.json, created if missing
     * @param typeRoots    type root directories (the probe contribution)
     */
    void mergeJsConfigTypeRoots(java.nio.file.Path jsconfigFile, List<String> typeRoots);

    /**
     * Writes {@code typeAcquisition.enable} into the top-level {@code typeAcquisition} of the
     * given {@code jsconfig.json}.
     *
     * <p>The script project ships the full probe declarations, and VS Code Automatic Type
     * Acquisition only guesses from JS dependency names and downloads @types packages over the
     * network. Offline or behind a proxy that slows down and can even stall the language server,
     * and it mixes in unrelated globals. {@code typeAcquisition} is a probe-owned object: it is
     * replaced as a whole, with the same semantics as {@link #mergeJsConfigIncludes}.
     *
     * @param jsconfigFile target jsconfig.json, created if missing
     * @param enable       whether to enable ATA (probe always contributes false)
     */
    default void mergeJsConfigTypeAcquisition(java.nio.file.Path jsconfigFile, boolean enable) {}

    /**
     * Merges extraPaths into the given {@code pyrightconfig.json} (deduplicated; a fresh file
     * also gets the default typeCheckingMode).
     *
     * @param pyrightFile target pyrightconfig.json, created if missing
     * @param extraPaths  relative paths to make sure are present
     */
    void mergePyrightExtraPaths(java.nio.file.Path pyrightFile, List<String> extraPaths);

    /**
     * Merges {@code python.analysis.extraPaths} into the VS Code workspace settings
     * ({@code <workspaceRoot>/.vscode/settings.json}, deduplicated, unknown keys kept).
     *
     * <p>Pylance discovers pyrightconfig.json inconsistently across versions and ways of opening
     * the workspace, while {@code .vscode/settings.json} is loaded natively by VS Code and is the
     * most reliable way to inject paths into Pylance. The pyright CLI keeps using
     * pyrightconfig.json. Both are written so each one covers the other.
     */
    default void mergeVscodePythonExtraPaths(java.nio.file.Path settingsFile, List<String> extraPaths) {}

    /**
     * Generic VS Code {@code .vscode/settings.json} injection: merges configuration fragments
     * declared by any backend into the workspace settings.
     *
     * <p>The merge strategy comes from {@link VscodeSetting#mode()} on each entry. Apart from
     * leaf keys declared as replace, every other key in the existing file is kept, including user
     * keys and keys injected by other backends; probe only adds or removes keys it owns. Object
     * contributions ({@link VscodeSettingMerge#MERGE_OBJECT}) merge recursively so that several
     * backends can each add their own entries to the same settings object (such as
     * {@code files.exclude}) without overwriting each other.
     *
     * @param settingsFile target {@code <workspaceRoot>/.vscode/settings.json}, created if missing
     * @param settings     contributions to merge, applied in order
     */
    default void mergeVscodeSettings(java.nio.file.Path settingsFile, List<VscodeSetting> settings) {}

    /** Merge strategy for one leaf key in {@link #mergeVscodeSettings}. */
    enum VscodeSettingMerge {
        /** probe owns this key: always replace it with the contribution (e.g. the TS language
         * service switch). */
        SET,
        /** Write only when the key is absent: keep the user's value once they set it explicitly
         * (e.g. {@code python.languageServer}). */
        SET_IF_ABSENT,
        /** The contribution must be a JSON object: merge it recursively into the existing object,
         * so contributed leaves replace and user or other-backend leaves are kept. */
        MERGE_OBJECT,
        /** The contribution must be a JSON string array: append it to the existing array with
         * string deduplication, keeping user paths. */
        EXTEND_STRING_ARRAY,
    }

    /**
     * One VS Code settings injection: a dotted path (such as {@code python.analysis.extraPaths}),
     * a value and a merge strategy. {@code value} takes any plain value Gson can serialize
     * directly (String/Number/Boolean/List/Map).
     */
    record VscodeSetting(String key, Object value, VscodeSettingMerge mode) {
        public VscodeSetting {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("vscode setting key must not be blank");
            if (mode == null) throw new IllegalArgumentException("vscode setting merge mode must not be null");
        }
    }

    /**
     * Merges editor snippets into a VS Code {@code .code-snippets} file. Snippet names owned by
     * probe are replaced; user snippets are kept.
     *
     * @param snippetsFile target .code-snippets (e.g.
     *                     {@code nekojs/.vscode/nekojs.code-snippets}), created if missing
     * @param snippets     snippets to write
     */
    void mergeVscodeSnippets(java.nio.file.Path snippetsFile, List<Snippet> snippets);
}
