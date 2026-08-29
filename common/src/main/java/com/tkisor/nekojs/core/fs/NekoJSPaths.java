package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
            synchronized (NekoJSPaths.class) {
                inst = INSTANCE;
                if (inst == null) {
                    INSTANCE = inst = fromGameDir(Platform.getGameDir());
                }
            }
        }
        return inst;
    }

    public static NekoJSPaths fromGameDir(Path gameDir) {
        return new NekoJSPaths(Objects.requireNonNull(gameDir, "gameDir"));
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
    private final Path legacyEngineConfig;
    private final Path probeConfig;
    private final Path assets;
    private final Path data;
    private final Path packsRoot;
    private final Path serverPacks;
    private final Set<Path> scriptRoots;

    private NekoJSPaths(Path gameDir) {
        // Windows 上 gameDir 可能带 8.3 短名（如 C:\Users\RUNNER~1\...）或大小写与磁盘实际形式
        // 不一致：与 toRealPath() 的结果混用会让 startsWith 判定全部失配。构造时统一成 real
        // 形式，所有派生路径（root/scripts/config...）随之同源；取 real 失败时退回逻辑形式。
        this.gameDir = toRealForm(gameDir.toAbsolutePath().normalize());
        this.root = this.gameDir.resolve("nekojs");
        this.startupScripts = root.resolve("startup_scripts");
        this.serverScripts = root.resolve("server_scripts");
        this.clientScripts = root.resolve("client_scripts");
        this.testScripts = root.resolve("test_scripts");
        this.probeDir = this.gameDir.resolve(".neko_probe");
        this.nodeModules = root.resolve("node_modules");
        this.config = root.resolve("config");
        this.readme = root.resolve("README.txt");
        // 引擎配置与 probe.toml 同目录：<gamedir>/nekojs/config/engine.toml——所有 NekoJS
        // 配置集中在 nekojs/config/ 下，与脚本目录并列，方便打包分发与查找。
        // 旧位置 <gamedir>/config/nekojs-engine.toml（安全加固期间短暂使用）仅作只读回退（见 legacyEngineConfig）。
        this.engineConfig = config.resolve("engine.toml");
        this.legacyEngineConfig = this.gameDir.resolve("config").resolve("nekojs-engine.toml");
        this.probeConfig = config.resolve("probe.toml");
        this.assets = root.resolve("assets");
        this.data = root.resolve("data");
        this.packsRoot = root.resolve(ScriptPackRegistry.GLOBAL_PACKS_DIR);
        this.serverPacks = root.resolve("server_packs");
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
    /** 旧版引擎配置位置 {@code <gamedir>/config/nekojs-engine.toml}（仅作只读迁移回退）。 */
    public Path legacyEngineConfig() { return legacyEngineConfig; }
    public Path probeConfig() { return probeConfig; }
    public Path assets() { return assets; }
    public Path data() { return data; }
    /** 脚本包根目录 {@code <gamedir>/nekojs/packs/}（WORLD 包在存档侧，不在此）。 */
    public Path packsRoot() { return packsRoot; }
    /** 服务器下发脚本包的客户端缓存根 {@code <gamedir>/nekojs/server_packs/}（按服务器地址哈希分桶）。 */
    public Path serverPacks() { return serverPacks; }

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

    /**
     * 路径的公共规范形式（见 {@code canonicalSplicedForm}）：自身存在则 toRealPath，
     * 否则最近存在祖先 real 形式拼接缺失后缀。跨调用方统一比较/relativize 用。
     */
    public static Path canonicalForm(Path path) {
        return canonicalSplicedForm(path);
    }

    /**
     * 判断路径是否位于给定根内，两侧都取规范比较形式（存在→toRealPath，缺失→最近存在
     * 祖先 real + 缺失后缀拼接）。调用方持有未规范化的根（如含 8.3 短名/大小写别名的
     * 临时目录）时，与 {@link #verifyInsideGameDir} 等返回的规范形式直接 startsWith 会失配。
     */
    public static boolean isInside(Path path, Path root) {
        return canonicalForm(path).startsWith(canonicalForm(root));
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
        if (!target.startsWith(canonicalSplicedForm(root))) {
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
        Path normalized = canonicalSplicedForm(path);
        return scriptRoots.stream()
                .map(rt -> canonicalSplicedForm(rt.normalize().toAbsolutePath()))
                .anyMatch(normalized::startsWith);
    }

    public boolean isSupportedScriptFile(Path path) {
        return ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(path);
    }

    /* ================= 内部工具 ================= */

    /** 存在则返回 toRealPath 形式（解析 8.3 短名/大小写/symlink），否则原样返回。 */
    private static Path toRealForm(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path;
        } catch (IOException e) {
            return path;
        }
    }

    private Path resolveAgainstWorkingDirectory(String path, Path currentWorkingDirectory) {
        Path parsed = Path.of(path);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        Path base = currentWorkingDirectory == null ? gameDir : currentWorkingDirectory;
        return base.resolve(parsed);
    }

    private static Path nearestExistingAncestorOrSelf(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 路径的规范比较形式：自身存在则 {@code toRealPath()}；否则取最近存在祖先的 real 形式
     * 拼接缺失后缀。缺失根目录（如尚未 initFolders 的 {@code nekojs/}）与缺失目标路径都
     * 用同一规则生成形式，比较才不因 8.3 短名/大小写拼写差异失配。
     */
    private static Path canonicalSplicedForm(Path path) {
        Path abs = path.normalize().toAbsolutePath();
        Path anchor = nearestExistingAncestorOrSelf(abs);
        if (anchor == null) {
            return abs;
        }
        try {
            Path anchorReal = anchor.toRealPath();
            return anchor.getNameCount() == abs.getNameCount()
                    ? anchorReal
                    : anchorReal.resolve(abs.subpath(anchor.getNameCount(), abs.getNameCount()));
        } catch (IOException e) {
            return abs;
        }
    }

    /**
     * 根包含性校验（ForCreate 与普通校验共用同一物理包含不变量）：
     * <ul>
     *   <li>目标已存在：以 {@code toRealPath()} 的 real 形式判定并返回——同时抓住 symlink
     *       逃逸与 8.3 短名/大小写失配（Windows 的 {@code C:\Users\RUNNER~1\...} 与
     *       toRealPath 解析出的长名混用曾是全部误判来源）；</li>
     *   <li>目标缺失：以拼接形式判定——最近存在祖先的 real 形式（解析全部 symlink/短名）
     *       拼上缺失后缀。已存在前缀里的 symlink 父目录逃逸在此被抓住；别名拼写的新路径
     *       也因两侧同规则拼接而不被误拒。返回该规范形式，同一逻辑路径不因调用方拼写不同
     *       而产生不同结果。</li>
     * </ul>
     */
    private Path verifyInsideRoot(Path path, Path rt) throws IOException {
        Path rootAbs = rt.normalize().toAbsolutePath();
        Path rootForm = canonicalSplicedForm(rootAbs);
        Path normalized = path.normalize().toAbsolutePath();
        if (Files.exists(normalized)) {
            Path realPath = normalized.toRealPath();
            if (!realPath.startsWith(rootForm)) {
                throw new IOException("Symlink escape detected: " + realPath);
            }
            return realPath;
        }
        Path spliced = canonicalSplicedForm(normalized);
        if (!spliced.startsWith(rootForm)) {
            if (normalized.startsWith(rootAbs) || normalized.startsWith(rootForm)) {
                // 逻辑上在根内，但已存在前缀解析后指向根外——symlink 祖先逃逸
                throw new IOException("Symlink escape detected: " + spliced);
            }
            throw new IOException("Access outside allowed root is forbidden: " + normalized);
        }
        return spliced;
    }

    private Path verifyInsideRootForCreate(Path path, Path rt) throws IOException {
        return verifyInsideRoot(path, rt);
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create directory: {}", dir, e);
        }
    }
}
