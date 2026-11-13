package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.probe.EditorConfigContributor;
import com.tkisor.nekojs.probe.FileEditorConfigContributor;
import com.tkisor.nekojs.probe.ProbeContext;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Editor config contributed by the Python backend, split out of {@link PythonProbeBackend}, which now only
 * renders: it merges extraPaths into pyrightconfig.json idempotently, one copy each for the game root, the
 * nekojs root, the four script dirs and every nested dir that contains .py files (Pylance only reads the
 * config at the workspace root), and writes the Pylance keys in {@code .vscode/settings.json}.
 */
final class PythonEditorConfig {

    private PythonEditorConfig() {
    }

    /**
     * Merges this backend's output dir ({@code .neko_probe/python}) into {@code extraPaths} in pyrightconfig.json.
     * The merge is idempotent and deduplicated; a fresh file also gets the defaults.
     *
     * <p>Writes one copy for every directory the user may open as a workspace: Pylance only reads the
     * pyrightconfig.json at the workspace root (only the pyright CLI discovers a config by walking up from the
     * source file), so a single nested config is ignored when the user opens the game directory or a script
     * directory, and {@code from nekojs import *} then fails to resolve and has no completion. This matches the
     * TS side, which writes one jsconfig per script dir: the nekojs root, the four script dirs, the game root,
     * and every nested dir under a script root that actually contains {@code .py} files. For the same
     * directories, {@code .vscode/settings.json} is written through the shared injection path
     * ({@link EditorConfigContributor#mergeVscodeSettings}), which appends to
     * {@code python.analysis.extraPaths} without duplicates and sets {@code python.languageServer} to Pylance
     * only when the user has not chosen one; existing user keys are kept.
     */
    static void contribute(EditorConfigContributor contributor, ProbeContext ctx) {
        com.tkisor.nekojs.core.fs.NekoJSPaths paths = ctx.paths();
        Path out = ctx.languageDir();
        contributePyright(contributor, paths.root().resolve("pyrightconfig.json"),
                paths.root().resolve(".vscode").resolve("settings.json"),
                FileEditorConfigContributor.relativePosix(paths.root(), out));
        for (Path scriptDir : List.of(paths.startupScripts(), paths.serverScripts(),
                paths.clientScripts(), paths.testScripts())) {
            contributePyright(contributor, scriptDir.resolve("pyrightconfig.json"),
                    scriptDir.resolve(".vscode").resolve("settings.json"),
                    FileEditorConfigContributor.relativePosix(scriptDir, out));
            mergePyrightConfigsForNestedPythonDirs(contributor, scriptDir, out);
        }
        contributePyright(contributor, paths.gameDir().resolve("pyrightconfig.json"),
                paths.gameDir().resolve(".vscode").resolve("settings.json"),
                FileEditorConfigContributor.relativePosix(paths.gameDir(), out));
    }

    /**
     * Deletes the pyrightconfig.json files this backend manages (root, game dir, each script dir). The shared
     * {@code .vscode/settings.json} is kept; the keys contributed to it are corrected idempotently by the next
     * contribute. pyrightconfig files derived for nested Python dirs are not tracked; the next scan merges them
     * again and leftovers are harmless.
     */
    static void reset(com.tkisor.nekojs.core.fs.NekoJSPaths paths) {
        for (Path pyright : List.of(
                paths.root().resolve("pyrightconfig.json"),
                paths.gameDir().resolve("pyrightconfig.json"),
                paths.startupScripts().resolve("pyrightconfig.json"),
                paths.serverScripts().resolve("pyrightconfig.json"),
                paths.clientScripts().resolve("pyrightconfig.json"),
                paths.testScripts().resolve("pyrightconfig.json"))) {
            try {
                Files.deleteIfExists(pyright);
            } catch (IOException e) {
                // A failed delete leaves a stale config: the next workspace open points extraPaths at the
                // deleted stub dir.
                com.tkisor.nekojs.core.error.Diagnostics.report(
                        "probe-python",
                        com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                        "删除过期的 pyrightconfig.json 失败（可能被编辑器占用）: " + pyright, e);
            }
        }
    }

    /**
     * Writes the same extraPath to both pyrightconfig.json (for the CLI) and .vscode/settings.json (for Pylance),
     * so either one covers the other.
     */
    private static void contributePyright(EditorConfigContributor contributor, Path pyrightFile,
                                          Path vscodeSettings, String relativeExtraPath) {
        contributor.mergePyrightExtraPaths(pyrightFile, List.of(relativeExtraPath));
        contributor.mergeVscodeSettings(vscodeSettings, List.of(
                new EditorConfigContributor.VscodeSetting("python.languageServer", "Pylance",
                        EditorConfigContributor.VscodeSettingMerge.SET_IF_ABSENT),
                new EditorConfigContributor.VscodeSetting("python.analysis.extraPaths", List.of(relativeExtraPath),
                        EditorConfigContributor.VscodeSettingMerge.EXTEND_STRING_ARRAY)));
    }

    /**
     * Maximum depth of the nested pyrightconfig scan, relative to the script root. Keeps a huge workspace from
     * being walked in full.
     */
    private static final int NESTED_SCAN_MAX_DEPTH = 8;

    /**
     * Directory names the nested pyrightconfig scan skips: build output, dependencies, caches and VCS. They hold
     * no user scripts, so walking them only wastes IO; {@code .vscode} holds config rather than scripts.
     */
    private static final Set<String> NESTED_SCAN_IGNORED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "__pycache__", "node_modules",
            "build", "dist", "out", "target", ".pnpm-store", ".dsh-tmp");

    /**
     * Writes a pyrightconfig.json to every subdirectory under a script root that actually contains .py files.
     *
     * <p>Pylance only reads the config at the workspace root, and the user may open the game dir, the nekojs dir
     * or one of the four script roots, or open a subdirectory such as {@code server_scripts/src} as the
     * workspace. Placing a config next to every dir that contains .py files (with extraPaths computed from the
     * real relative depth of that dir to the stub output) lets {@code from nekojs import *} resolve whichever dir
     * is the workspace root. Dirs with only JS get nothing, since the Python backend does not need to contribute
     * pyright config for them.
     *
     * <p>The scan is bounded: depth stops at {@link #NESTED_SCAN_MAX_DEPTH} and it ignores the
     * {@link #NESTED_SCAN_IGNORED_DIRS} subtrees (node_modules, build output and the like hold no user scripts,
     * and a full walk is expensive in a large workspace).
     */
    private static void mergePyrightConfigsForNestedPythonDirs(EditorConfigContributor contributor,
                                                                Path scriptDir, Path out) {
        if (scriptDir == null || !Files.isDirectory(scriptDir)) {
            return;
        }
        Set<Path> pythonDirs = new TreeSet<>();
        try {
            Files.walkFileTree(scriptDir, Set.of(), NESTED_SCAN_MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(scriptDir)) return FileVisitResult.CONTINUE;
                    Path name = dir.getFileName();
                    return name != null && NESTED_SCAN_IGNORED_DIRS.contains(name.toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path name = file.getFileName();
                    if (name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".py")) {
                        Path parent = file.getParent();
                        if (parent != null && !parent.equals(scriptDir)) {
                            pythonDirs.add(parent);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // A failed nested pyright scan leaves subdirectory workspaces without completions and without any trace.
            com.tkisor.nekojs.core.error.Diagnostics.report(
                    "probe-python",
                    com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                    "扫描 " + scriptDir + " 下的嵌套 Python 目录失败，这些子目录将不生成 pyrightconfig.json"
                            + "（Pylance 补全可能不可用）", e);
            return;
        }
        for (Path dir : pythonDirs) {
            contributePyright(contributor, dir.resolve("pyrightconfig.json"),
                    dir.resolve(".vscode").resolve("settings.json"),
                    FileEditorConfigContributor.relativePosix(dir, out));
        }
    }

}
