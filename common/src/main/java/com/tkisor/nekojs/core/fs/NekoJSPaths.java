package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 负责定义模组用到的所有文件路径及基础目录初始化。
 *
 * <p>实例类：通过 {@link #fromGameDir(Path)} 创建，常用路径通过实例 getter 获取，
 * 路径校验通过实例方法执行。旧 {@code public static final Path} 字段保留为过渡 facade，
 * 供尚未迁移到实例依赖的调用点使用；{@link #legacy()} 是 bootstrap 同步的专用 framework seam，
 * 返回由 {@link #fromGameDir(Path)} 创建的过渡实例。两者均在后续 Phase 删除。
 */
public final class NekoJSPaths {

    /* ================= Legacy static field facade（后续 Phase 删除） ================= */
    public static final Path GAME_DIR = Platform.getGameDir();
    public static final Path ROOT = GAME_DIR.resolve("nekojs");

    /* ================= 脚本目录 ================= */
    public static final Path STARTUP_SCRIPTS = ROOT.resolve("startup_scripts");
    public static final Path SERVER_SCRIPTS  = ROOT.resolve("server_scripts");
    public static final Path CLIENT_SCRIPTS  = ROOT.resolve("client_scripts");
    public static final Path TEST_SCRIPTS    = ROOT.resolve("test_scripts");

    public static final Path PROBE_DIR = GAME_DIR.resolve(".neko_probe");
    public static final Path NODE_MODULES = ROOT.resolve("node_modules");

    /* ================= 配置与辅助文件 ================= */
    public static final Path CONFIG = ROOT.resolve("config");
    public static final Path README = ROOT.resolve("README.txt");
    public static final Path ENGINE_CONFIG = CONFIG.resolve("engine.toml");

    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path DATA   = ROOT.resolve("data");

    private static final NekoJSPaths LEGACY = fromGameDir(GAME_DIR);

    /* ================= 实例状态 ================= */
    private final Path gameDir;
    private final Path root;
    private final Path startupScripts;
    private final Path serverScripts;
    private final Path clientScripts;
    private final Path testScripts;
    private final Path probeDir;
    private final Path nodeModules;
    private final Path config;
    private final Path readme;
    private final Path engineConfig;
    private final Path assets;
    private final Path data;
    private final Set<Path> scriptRoots;

    private NekoJSPaths(Path gameDir) {
        this.gameDir = gameDir;
        this.root = gameDir.resolve("nekojs");
        this.startupScripts = root.resolve("startup_scripts");
        this.serverScripts = root.resolve("server_scripts");
        this.clientScripts = root.resolve("client_scripts");
        this.testScripts = root.resolve("test_scripts");
        this.probeDir = gameDir.resolve(".neko_probe");
        this.nodeModules = root.resolve("node_modules");
        this.config = root.resolve("config");
        this.readme = root.resolve("README.txt");
        this.engineConfig = config.resolve("engine.toml");
        this.assets = root.resolve("assets");
        this.data = root.resolve("data");
        this.scriptRoots = Set.of(startupScripts, serverScripts, clientScripts, testScripts);
    }

    public static NekoJSPaths fromGameDir(Path gameDir) {
        return new NekoJSPaths(gameDir);
    }

    public static NekoJSPaths legacy() {
        return LEGACY;
    }

    /* ================= 实例 getter ================= */
    public Path gameDir() { return gameDir; }
    public Path root() { return root; }
    public Path startupScripts() { return startupScripts; }
    public Path serverScripts() { return serverScripts; }
    public Path clientScripts() { return clientScripts; }
    public Path testScripts() { return testScripts; }
    public Path probeDir() { return probeDir; }
    public Path nodeModules() { return nodeModules; }
    public Path config() { return config; }
    public Path readme() { return readme; }
    public Path engineConfig() { return engineConfig; }
    public Path assets() { return assets; }
    public Path data() { return data; }

    /* ================= 实例路径校验与初始化 ================= */
    public void initFolders() {
        ensureDir(root);
        ensureDir(startupScripts);
        ensureDir(serverScripts);
        ensureDir(clientScripts);
        ensureDir(testScripts);
        ensureDir(config);
        ensureDir(probeDir);
        ensureDir(nodeModules);
        ensureDir(assets);
        ensureDir(data);
    }

    public Path verifyInsideGameDir(Path path) throws IOException {
        return verifyInsideRoot(path, gameDir);
    }

    public Path verifyInsideGameDirForCreate(Path path) throws IOException {
        return verifyInsideRootForCreate(path, gameDir);
    }

    public Path verifyInsideNekoRoot(Path path) throws IOException {
        return verifyInsideRoot(path, root);
    }

    public Path verifyInsideNekoRootForCreate(Path path) throws IOException {
        return verifyInsideRootForCreate(path, root);
    }

    public Path resolveGamePath(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideGameDir(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public Path resolveGamePathForCreate(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideGameDirForCreate(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public Path resolveNekoWritePath(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideNekoRoot(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public Path resolveNekoWritePathForCreate(String path, Path currentWorkingDirectory) throws IOException {
        return verifyInsideNekoRootForCreate(resolveAgainstWorkingDirectory(path, currentWorkingDirectory));
    }

    public Path verifyScriptSyncPath(String relativePath) throws IOException {
        Path parsed = Path.of(relativePath).normalize();
        if (parsed.isAbsolute() || parsed.startsWith("..")) {
            throw new IOException("Invalid script path: " + relativePath);
        }

        Path target = verifyInsideGameDir(root.resolve(parsed));
        if (!target.startsWith(root.toAbsolutePath().normalize())) {
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

    public boolean isInsideScriptRoot(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        return scriptRoots.stream()
                .map(rt -> rt.normalize().toAbsolutePath())
                .anyMatch(normalized::startsWith);
    }

    private Path resolveAgainstWorkingDirectory(String path, Path currentWorkingDirectory) {
        Path parsed = Path.of(path);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        Path base = currentWorkingDirectory == null ? gameDir : currentWorkingDirectory;
        return base.resolve(parsed);
    }

    private Path verifyInsideRoot(Path path, Path rt) throws IOException {
        Path normalizedRoot = rt.normalize().toAbsolutePath();
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

    private Path verifyInsideRootForCreate(Path path, Path rt) throws IOException {
        Path normalizedRoot = rt.normalize().toAbsolutePath();
        Path normalized = verifyInsideRoot(path, rt);
        Path existingParent = nearestExistingParent(normalized);
        if (existingParent != null) {
            Path realParent = existingParent.toRealPath();
            if (!realParent.startsWith(normalizedRoot)) {
                throw new IOException("Symlink parent escape detected: " + realParent);
            }
        }
        return normalized;
    }

    private Path nearestExistingParent(Path path) {
        Path current = path.getParent();
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /* ================= Legacy static facade 方法（后续 Phase 删除） ================= */
    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create directory: {}", dir, e);
        }
    }

    public static boolean isSupportedScriptFile(Path path) {
        return ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(path);
    }
}
