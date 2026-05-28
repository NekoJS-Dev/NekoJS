package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 负责定义模组用到的所有文件路径及基础目录初始化
 */
public final class NekoJSPaths {

    /* ================= 基础根路径 ================= */
    public static final Path GAME_DIR = Platform.getGameDir();
    public static final Path ROOT = GAME_DIR.resolve("nekojs");

    /* ================= 脚本目录 ================= */
    public static final Path STARTUP_SCRIPTS = ROOT.resolve("startup_scripts");
    public static final Path SERVER_SCRIPTS  = ROOT.resolve("server_scripts");
    public static final Path CLIENT_SCRIPTS  = ROOT.resolve("client_scripts");
    public static final Path TEST_SCRIPTS    = ROOT.resolve("test_scripts");

    // 自动补全用的类型定义目录
    public static final Path PROBE_DIR = GAME_DIR.resolve(".neko_probe");
    public static final Path NODE_MODULES = ROOT.resolve("node_modules");

    /* ================= 配置与辅助文件 ================= */
    public static final Path CONFIG = ROOT.resolve("config");
    public static final Path README = ROOT.resolve("README.txt");
    public static final Path ENGINE_CONFIG = CONFIG.resolve("engine.toml");

    // 资源
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path DATA   = ROOT.resolve("data");

    private static final Set<Path> SCRIPT_ROOTS = Set.of(STARTUP_SCRIPTS, SERVER_SCRIPTS, CLIENT_SCRIPTS, TEST_SCRIPTS);

    /**
     * 仅初始化物理文件夹，不涉及具体文件内容生成
     */
    public static void initFolders() {
        ensureDir(ROOT);
        ensureDir(STARTUP_SCRIPTS);
        ensureDir(SERVER_SCRIPTS);
        ensureDir(CLIENT_SCRIPTS);
        ensureDir(TEST_SCRIPTS);
        ensureDir(CONFIG);
        ensureDir(PROBE_DIR);
        ensureDir(NODE_MODULES);
        ensureDir(ASSETS);
        ensureDir(DATA);

    }

    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create directory: {}", dir, e);
        }
    }

    /**
     * 校验路径是否在游戏目录内，防止符号链接越权访问
     */
    public static Path verifyInsideGameDir(Path path) throws IOException {
        return verifyInsideRoot(path, GAME_DIR);
    }

    public static Path verifyInsideGameDirForCreate(Path path) throws IOException {
        return verifyInsideRootForCreate(path, GAME_DIR);
    }

    public static Path verifyInsideNekoRoot(Path path) throws IOException {
        return verifyInsideRoot(path, ROOT);
    }

    public static Path verifyInsideNekoRootForCreate(Path path) throws IOException {
        return verifyInsideRootForCreate(path, ROOT);
    }

    public static Path resolveGamePath(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideGameDir(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public static Path resolveGamePathForCreate(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideGameDirForCreate(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public static Path resolveNekoWritePath(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideNekoRoot(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public static Path resolveNekoWritePathForCreate(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideNekoRootForCreate(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    private static Path resolveAgainstWorkingDirectory(String path, Path currentWorkingDirectory) {
        Path parsed = Path.of(path);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        Path base = currentWorkingDirectory == null ? GAME_DIR : currentWorkingDirectory;
        return base.resolve(parsed);
    }

    private static Path verifyInsideRoot(Path path, Path root) throws IOException {
        Path normalizedRoot = root.normalize().toAbsolutePath();
        Path normalized = path.normalize().toAbsolutePath();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IOException("Access outside allowed root is forbidden: " + normalized);
        }
        if (Files.exists(normalized)) {
            Path realPath = normalized.toRealPath();
            if (!realPath.startsWith(normalizedRoot)) {
                throw new IOException("Symlink escape detected: " + realPath);
            }
        }
        return normalized;
    }

    private static Path verifyInsideRootForCreate(Path path, Path root) throws IOException {
        Path normalizedRoot = root.normalize().toAbsolutePath();
        Path normalized = verifyInsideRoot(path, root);
        Path existingParent = nearestExistingParent(normalized);
        if (existingParent != null) {
            Path realParent = existingParent.toRealPath();
            if (!realParent.startsWith(normalizedRoot)) {
                throw new IOException("Symlink parent escape detected: " + realParent);
            }
        }
        return normalized;
    }

    private static Path nearestExistingParent(Path path) {
        Path current = path.getParent();
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public static Path verifyScriptSyncPath(String relativePath) throws IOException {
        Path parsed = Path.of(relativePath).normalize();
        if (parsed.isAbsolute() || parsed.startsWith("..")) {
            throw new IOException("Invalid script path: " + relativePath);
        }

        Path target = verifyInsideGameDir(ROOT.resolve(parsed));
        if (!target.startsWith(ROOT.toAbsolutePath().normalize())) {
            throw new IOException("Access outside NekoJS workspace is forbidden: " + relativePath);
        }
        if (!isSupportedScriptFile(target)) {
            throw new IOException("Unsupported script file type: " + relativePath);
        }
        if (!isInsideScriptRoot(target)) {
            throw new IOException("Script sync is only allowed inside startup_scripts, server_scripts, client_scripts, or test_scripts: " + relativePath);
        }
        return target;
    }

    public static boolean isSupportedScriptFile(Path path) {
        return ScriptCompilerRegistry.current().isSupportedScriptFile(path);
    }

    public static boolean isInsideScriptRoot(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        return SCRIPT_ROOTS.stream()
                .map(root -> root.normalize().toAbsolutePath())
                .anyMatch(normalized::startsWith);
    }

    private NekoJSPaths() {}
}