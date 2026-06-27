package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * NekoJS 文件路径单例：通过 {@link #get()} 获取全局实例。
 *
 * <p>不可变——创建后所有路径不变。实例方法获取路径，静态方法执行校验。
 */
public final class NekoJSPaths {

    private static volatile NekoJSPaths INSTANCE;

    public static NekoJSPaths get() {
        NekoJSPaths inst = INSTANCE;
        if (inst == null) {
            INSTANCE = new NekoJSPaths(Platform.getGameDir());
        }
        return INSTANCE;
    }

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

    /* ================= 实例路径初始化 ================= */
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

    /* ================= 路径校验（实例方法） ================= */
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

    public boolean isSupportedScriptFile(Path path) {
        return ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(path);
    }

    /* ================= 内部工具 ================= */
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

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create directory: {}", dir, e);
        }
    }
}
