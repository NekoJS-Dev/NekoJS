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
 * Python backend 的编辑器配置贡献（从 {@link PythonProbeBackend} 拆出，后者只剩渲染职责）：
 * pyrightconfig.json 的 extraPaths 幂等合并（游戏根/nekojs 根/四个脚本目录/含 .py 的嵌套目录
 * 各一份——Pylance 只读工作区根配置）+ {@code .vscode/settings.json} 的 Pylance 键。
 */
final class PythonEditorConfig {

    private PythonEditorConfig() {
    }

    /**
     * 把本 backend 的输出目录（{@code .neko_probe/python}）合并进 pyrightconfig.json 的
     * {@code extraPaths}（幂等、去重；fresh 文件附带默认）。
     *
     * <p>**每个可能被当作工作区打开的目录都写一份**：Pylance 只读取「工作区根」的
     * pyrightconfig.json（pyright CLI 才会从源文件就近向上发现），单一嵌套配置在用户打开
     * 游戏目录 / 脚本目录时会被忽略，导致 {@code from nekojs import *} 无法解析、无补全。
     * 与 TS 侧 jsconfig「每个脚本目录一份」的策略对齐：nekojs/ 根、四个脚本目录、游戏根目录，
     * 以及脚本根下每个实际包含 {@code .py} 文件的嵌套目录。
     * 同一批目录的 {@code .vscode/settings.json} 则经通用注入机制
     * （{@link EditorConfigContributor#mergeVscodeSettings}）写入
     * {@code python.analysis.extraPaths}（去重追加）与 {@code python.languageServer}
     * （仅当用户未显式选择时固定 Pylance），保留用户既有键。
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
     * 删除本 backend 管理的 pyrightconfig.json（根/游戏目录/各脚本目录）。共享的
     * {@code .vscode/settings.json} 不删，其中的贡献键由下次 contribute 幂等校正；
     * 嵌套 Python 目录的衍生 pyrightconfig 不追踪（下次扫描会重新合并，残留无害）。
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
                NekoJS.LOGGER.debug("EditorConfig: failed to delete pyrightconfig {}", pyright, e);
            }
        }
    }

    /** 同一个 extraPath 同时写入 pyrightconfig（CLI 侧）与 .vscode/settings.json（Pylance 侧），互为兜底。 */
    private static void contributePyright(EditorConfigContributor contributor, Path pyrightFile,
                                          Path vscodeSettings, String relativeExtraPath) {
        contributor.mergePyrightExtraPaths(pyrightFile, List.of(relativeExtraPath));
        contributor.mergeVscodeSettings(vscodeSettings, List.of(
                new EditorConfigContributor.VscodeSetting("python.languageServer", "Pylance",
                        EditorConfigContributor.VscodeSettingMerge.SET_IF_ABSENT),
                new EditorConfigContributor.VscodeSetting("python.analysis.extraPaths", List.of(relativeExtraPath),
                        EditorConfigContributor.VscodeSettingMerge.EXTEND_STRING_ARRAY)));
    }

    /** 嵌套 pyrightconfig 扫描的最大深度（相对脚本根；防超大工作区全量 walk）。 */
    private static final int NESTED_SCAN_MAX_DEPTH = 8;

    /**
     * 嵌套 pyrightconfig 扫描跳过的目录名（构建产物/依赖/缓存/VCS——它们不含用户脚本，
     * 全量 walk 只浪费 IO；{@code .vscode} 含的是配置而非脚本）。
     */
    private static final Set<String> NESTED_SCAN_IGNORED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "__pycache__", "node_modules",
            "build", "dist", "out", "target", ".pnpm-store", ".dsh-tmp");

    /**
     * 为脚本根目录下每个「实际包含 .py 脚本」的子目录写一份 pyrightconfig.json。
     *
     * <p>Pylance 只读取工作区根的配置，而用户既可能打开游戏目录 / nekojs 目录 / 四个脚本根，
     * 也可能直接把某个 {@code server_scripts/src} 之类的子目录打开为工作区。给每个含 .py 的
     * 目录就近放置一份配置（extraPaths 按该目录到 stub 输出的真实相对深度计算），任一目录被
     * 当作工作区根时都能解析 {@code from nekojs import *}。JS-only 目录不写（Python backend
     * 无需为其贡献 pyright 配置）。
     *
     * <p>扫描有界：深度上限 {@link #NESTED_SCAN_MAX_DEPTH}，忽略 {@link #NESTED_SCAN_IGNORED_DIRS}
     * 子树（node_modules/构建产物等不含用户脚本，全量 walk 在大型工作区代价明显）。
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
            NekoJS.LOGGER.debug("Probe [python]: failed to scan {} for nested pyright configs", scriptDir, e);
            return;
        }
        for (Path dir : pythonDirs) {
            contributePyright(contributor, dir.resolve("pyrightconfig.json"),
                    dir.resolve(".vscode").resolve("settings.json"),
                    FileEditorConfigContributor.relativePosix(dir, out));
        }
    }

}
