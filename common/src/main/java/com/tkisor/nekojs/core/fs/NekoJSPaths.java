package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private NekoJSPaths() {}
}