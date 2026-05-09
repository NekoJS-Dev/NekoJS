package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJSCommon;
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

    // 自动补全用的类型定义目录
    public static final Path PROBE_DIR = GAME_DIR.resolve(".probe");
    public static final Path NODE_MODULES = ROOT.resolve("node_modules");

    /* ================= 配置与辅助文件 ================= */
    public static final Path CONFIG = ROOT.resolve("config");
    public static final Path README = ROOT.resolve("README.txt");
    public static final Path ENGINE_CONFIG = CONFIG.resolve("engine.toml");

    // 资源
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path DATA   = ROOT.resolve("data");

    public static final Set<String> SCRIPT_EXTENSIONS = Set.of("js", "ts", "jsx", "tsx");
    private static final Set<Path> SCRIPT_ROOTS = Set.of(STARTUP_SCRIPTS, SERVER_SCRIPTS, CLIENT_SCRIPTS);

    /**
     * 仅初始化物理文件夹，不涉及具体文件内容生成
     */
    public static void initFolders() {
        ensureDir(ROOT);
        ensureDir(STARTUP_SCRIPTS);
        ensureDir(SERVER_SCRIPTS);
        ensureDir(CLIENT_SCRIPTS);
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
            NekoJSCommon.LOGGER.error("[NekoJS] Failed to create directory: {}", dir, e);
        }
    }

    /**
     * 校验路径是否在游戏目录内，防止符号链接越权访问
     */
    public static Path verifyInsideGameDir(Path path) throws IOException {
        Path normalized = path.normalize().toAbsolutePath();
        if (!normalized.startsWith(GAME_DIR)) {
            throw new IOException("Access outside game directory is forbidden: " + normalized);
        }
        if (Files.exists(normalized)) {
            Path realPath = normalized.toRealPath();
            if (!realPath.startsWith(GAME_DIR)) {
                throw new IOException("Symlink escape detected: " + realPath);
            }
        }
        return normalized;
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
            throw new IOException("Script sync is only allowed inside startup_scripts, server_scripts, or client_scripts: " + relativePath);
        }
        return target;
    }

    public static boolean isSupportedScriptFile(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 && SCRIPT_EXTENSIONS.contains(fileName.substring(dotIndex + 1));
    }

    public static boolean isInsideScriptRoot(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        return SCRIPT_ROOTS.stream()
                .map(root -> root.normalize().toAbsolutePath())
                .anyMatch(normalized::startsWith);
    }

    private NekoJSPaths() {}
}