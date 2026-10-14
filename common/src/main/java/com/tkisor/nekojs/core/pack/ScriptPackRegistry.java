package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 脚本包注册表：持有 GLOBAL（{@code nekojs/packs/}）、WORLD（{@code <world>/nekojs_packs/}，
 * 由平台在服务器启动/停止时激活/卸载）与 SERVER_CACHE（{@code nekojs/server_packs/<bucket>/}，
 * 客户端在多人包分发验签/信任通过后激活、断线时卸载）三批已扫描包，供 {@code ScriptLocator}
 * 发现脚本时并入。
 *
 * <p>实例可构造（测试注入临时目录），静态默认实例跟随 {@link NekoJSPaths}。GLOBAL 包
 * 在进程内首次访问时懒扫描一次，此后由显式 {@link #refreshGlobalPacks()} 更新
 * （reload 命令在脚本发现前调用，使启用态变化即时生效）。
 */
public final class ScriptPackRegistry {

    public static final String GLOBAL_PACKS_DIR = "packs";
    public static final String WORLD_PACKS_DIR = "nekojs_packs";

    private static final ScriptPackRegistry INSTANCE = new ScriptPackRegistry();

    public static ScriptPackRegistry get() {
        INSTANCE.refreshGlobalPacksOnce();
        return INSTANCE;
    }

    private volatile List<ScriptPack> globalPacks = List.of();
    private volatile List<ScriptPack> worldPacks = List.of();
    private volatile List<ScriptPack> serverCachePacks = List.of();
    private volatile List<ScriptPack> modPacks = List.of();
    private volatile boolean globalScanned;
    private Path globalRoot;
    private Path worldRoot;
    private long worldSession;
    private long cacheSession;
    private Path cacheRoot;

    /** Source instance is stable across replacements within a session and changes on remount. */
    public synchronized String instanceKey(ScriptPack pack) {
        long session = pack.scope() == ScriptPackScope.WORLD ? worldSession
                : pack.scope() == ScriptPackScope.SERVER_CACHE ? cacheSession : 0;
        return pack.scope() + ":" + pack.root().toAbsolutePath().normalize() + "#" + session;
    }

    /** Discovered snapshots, including authorized remote content; not active Contexts. */
    public synchronized List<ScriptPack> allPacks() {
        List<ScriptPack> packs = new ArrayList<>();
        packs.addAll(globalPacks);
        packs.addAll(modPacks);
        packs.addAll(worldPacks);
        packs.addAll(serverCachePacks);
        return List.copyOf(packs);
    }

    /** Computes author dependencies without claiming that providers have activated. */
    public synchronized PackDependencyPlan dependencyPlan() {
        return PackDependencyPlan.resolve(allPacks(), PackDependencyPlan.Environment.current());
    }

    /** Refreshes local sources without remounting a closed world or rescanning remote content. */
    public synchronized void refreshLocalPacks() {
        refreshGlobalPacks(globalRoot == null ? NekoJSPaths.get().packsRoot() : globalRoot);
        if (worldRoot != null) {
            worldPacks = scan(worldRoot, ScriptPackScope.WORLD);
        }
    }

    /** Selects, persists, then publishes local intent. Ambiguity and write failure never succeed. */
    public synchronized PackOperationResult setEnabled(String selector, boolean enabled) {
        refreshLocalPacks();
        List<ScriptPack> matches = allPacks().stream()
                .filter(pack -> matchesSelector(pack, selector)).toList();
        List<String> names = matches.stream().map(ScriptPack::selector).toList();
        if (matches.isEmpty()) {
            return new PackOperationResult(PackOperationResult.Status.NOT_FOUND, selector, names);
        }
        if (matches.size() > 1) {
            return new PackOperationResult(PackOperationResult.Status.AMBIGUOUS, selector, names);
        }
        ScriptPack pack = matches.getFirst();
        if (pack.scope() == ScriptPackScope.SERVER_CACHE || pack.scope() == ScriptPackScope.MOD) {
            return new PackOperationResult(PackOperationResult.Status.READ_ONLY, pack.selector(), names);
        }
        if (!ScriptPackState.save(pack.root(), enabled)) {
            return new PackOperationResult(PackOperationResult.Status.SAVE_FAILED, pack.selector(), names);
        }
        ScriptPack updated = new ScriptPack(pack.id(), pack.name(), pack.version(), pack.scope(),
                pack.root(), enabled, pack.manifest());
        if (pack.scope() == ScriptPackScope.GLOBAL) {
            globalPacks = globalPacks.stream().map(existing -> existing == pack ? updated : existing).toList();
        } else {
            worldPacks = worldPacks.stream().map(existing -> existing == pack ? updated : existing).toList();
        }
        return new PackOperationResult(PackOperationResult.Status.SAVED, pack.selector(), names);
    }

    private static boolean matchesSelector(ScriptPack pack, String selector) {
        if (selector == null) return false;
        if (pack.selector().equalsIgnoreCase(selector)) return true;
        int separator = selector.indexOf(':');
        if (separator < 0) return pack.id().equals(selector);
        return pack.scope().name().equalsIgnoreCase(selector.substring(0, separator))
                && pack.id().equals(selector.substring(separator + 1));
    }

    /** 全部启用包：GLOBAL（字母序）→ WORLD（字母序）→ SERVER_CACHE（字母序），与脚本加载顺序一致。 */
    public List<ScriptPack> enabledPacks() {
        List<ScriptPack> out = new ArrayList<>(globalPacks.size() + worldPacks.size() + serverCachePacks.size());
        for (ScriptPack pack : globalPacks) if (pack.enabled()) out.add(pack);
        for (ScriptPack pack : modPacks) if (pack.enabled()) out.add(pack);
        for (ScriptPack pack : worldPacks) if (pack.enabled()) out.add(pack);
        for (ScriptPack pack : serverCachePacks) if (pack.enabled()) out.add(pack);
        return out;
    }

    public List<ScriptPack> globalPacks() {
        return globalPacks;
    }

    /** Loader-owned content is materialized once before STARTUP and remains read-only. */
    public synchronized void discoverLoadedModPacks() {
        List<ScriptPack> discovered = new ArrayList<>();
        com.tkisor.nekojs.platform.Platform.instance().visitLoadedPackRoots((mod, path) -> discovered.addAll(
                LoadedModPackSource.discover(java.util.Map.of(mod, path), NekoJSPaths.get().root().resolve("mod_packs"))));
        modPacks = List.copyOf(discovered);
    }

    public List<ScriptPack> worldPacks() {
        return worldPacks;
    }

    /** 当前已激活的服务器缓存包（多人包分发，客户端侧调用）。 */
    public List<ScriptPack> serverCachePacks() {
        return serverCachePacks;
    }

    /** 首次调用时扫描一次全局包；幂等。 */
    private void refreshGlobalPacksOnce() {
        if (globalScanned) return;
        synchronized (this) {
            if (globalScanned) return;
            refreshGlobalPacks();
        }
    }

    /** 重扫全局包（reload 命令 / 包切换后调用）。 */
    public synchronized void refreshGlobalPacks() {
        refreshGlobalPacks(NekoJSPaths.get().packsRoot());
    }

    /** 测试/注入用：按指定根目录重扫全局包。 */
    public synchronized void refreshGlobalPacks(Path packsRoot) {
        globalRoot = packsRoot;
        globalScanned = true;
        globalPacks = scan(packsRoot, ScriptPackScope.GLOBAL);
    }

    /**
     * 激活世界包：扫描 {@code worldDir/nekojs_packs/} 并替换当前世界包集合。
     * 返回本次激活的全部 WORLD 包（含禁用者，供列表命令展示）。
     *
     * <p>worldDir is normalized to an absolute path first: platform entry points may pass a
     * relative form (a Windows dedicated server was observed handing in {@code .\world\.})
     * while the nekojs root is always absolute — keeping pack script paths relative makes the
     * later {@code root.relativize(script.path)} mix absolute and relative inputs, throw IAE,
     * and fail every WORLD pack script (ticket 03 §3-1 real-code defect, ticket 07 G4).
     */
    public synchronized List<ScriptPack> activateWorldPacks(Path worldDir) {
        worldSession++;
        Path normalized = worldDir == null ? null : worldDir.toAbsolutePath().normalize();
        worldRoot = normalized == null ? null : normalized.resolve(WORLD_PACKS_DIR);
        worldPacks = worldRoot == null
                ? List.of()
                : scan(worldRoot, ScriptPackScope.WORLD);
        return worldPacks;
    }

    /** 卸载世界包（serverStopped / 退出单人），返回被移除的包。 */
    public synchronized List<ScriptPack> deactivateWorldPacks() {
        worldSession++;
        List<ScriptPack> removed = worldPacks;
        worldPacks = List.of();
        worldRoot = null;
        return removed;
    }

    /**
     * 激活服务器缓存包（多人包分发，客户端侧）：扫描
     * {@code nekojs/server_packs/<bucket>/} 并替换当前 SERVER_CACHE 集合。
     * 服务器缓存包强制启用（manifest 默认值与状态文件均不适用），返回激活的全部包。
     */
    public synchronized List<ScriptPack> activateServerCachePacks(Path bucketDir) {
        updateCacheSession(bucketDir);
        serverCachePacks = bucketDir == null
            ? List.of()
            : scanForceEnabled(bucketDir, ScriptPackScope.SERVER_CACHE);
        return serverCachePacks;
    }

    /**
     * 激活指定同步清单中的服务器缓存包；缓存 bucket 中未选中的旧目录不参与扫描，物理文件仍保留。
     * {@code activePackIds} 使用传输层的 syncId（例如 {@code packs:demo}），而不是 manifest id。
     */
    public synchronized List<ScriptPack> activateServerCachePacks(Path bucketDir,
                                                                    Collection<String> activePackIds) {
        updateCacheSession(bucketDir);
        serverCachePacks = bucketDir == null
            ? List.of()
            : scanForceEnabled(bucketDir, ScriptPackScope.SERVER_CACHE, activePackIds);
        return serverCachePacks;
    }

    /** 卸载服务器缓存包（断线 / 服务端清空包集），返回被移除的包。缓存文件保留。 */
    public synchronized List<ScriptPack> deactivateServerCachePacks() {
        cacheSession++; cacheRoot = null;
        List<ScriptPack> removed = serverCachePacks;
        serverCachePacks = List.of();
        return removed;
    }

    private void updateCacheSession(Path root) {
        Path normalized = root == null ? null : root.toAbsolutePath().normalize();
        if (!java.util.Objects.equals(cacheRoot, normalized)) cacheSession++;
        cacheRoot = normalized;
    }

    /** 重置扫描标记（测试用：让下一次 get() 重新懒扫描）。 */
    synchronized void resetForTest() {
        modPacks = List.of();
        worldSession++; cacheSession++; cacheRoot = null;
        globalRoot = null;
        worldRoot = null;
        globalScanned = false;
        globalPacks = List.of();
        worldPacks = List.of();
        serverCachePacks = List.of();
    }

    /** 扫描目录下所有含 manifest.json 的子目录为包；损坏 manifest 跳过，同 scope 重复 id 后者跳过。 */
    private static List<ScriptPack> scan(Path root, ScriptPackScope scope) {
        return scan(root, scope, false, null);
    }

    /**
     * 扫描目录下所有含 manifest.json 的子目录为包；损坏 manifest 跳过，同 scope 重复 id 后者跳过。
     *
     * @param forceEnabled true 时忽略 manifest 默认值与状态文件强制启用（SERVER_CACHE 语义：
     *                     是否执行由验签/信任关口决定，包内启用开关不适用）
     */
    private static List<ScriptPack> scan(Path root, ScriptPackScope scope, boolean forceEnabled) {
        return scan(root, scope, forceEnabled, null);
    }

    private static List<ScriptPack> scan(Path root, ScriptPackScope scope, boolean forceEnabled,
                                         Set<String> selectedDirs) {
        if (root == null || !Files.isDirectory(root)) return List.of();
        List<Path> dirs;
        try (Stream<Path> stream = Files.list(root)) {
            dirs = stream.filter(Files::isDirectory)
                    .filter(dir -> selectedDirs == null || selectedDirs.contains(dir.getFileName().toString()))
                    .sorted().toList();
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to list script pack directory {}: {}", root, e.toString());
            return List.of();
        }
        List<ScriptPack> packs = new ArrayList<>();
        for (Path dir : dirs) {
            String fallbackId = ScriptPackManifest.sanitizeId(dir.getFileName().toString());
            ScriptPackManifest manifest = ScriptPackManifest.load(dir, fallbackId);
            if (manifest == null) continue; // 无 manifest（非包）或损坏（已 WARN）
            if (manifest.descriptor() != null) {
                try {
                    manifest.descriptor().validateFiles(dir, com.tkisor.nekojs.core.ScriptFilePolicy.legacyRuntime());
                } catch (IllegalArgumentException invalid) {
                    NekoJS.LOGGER.warn("[NEKO-1016] Pack entries rejected; pack skipped: {}", dir, invalid);
                    continue;
                }
            }
            String id = ScriptPackManifest.sanitizeId(manifest.id());
            if (!id.equals(manifest.id())) {
                NekoJS.LOGGER.warn("Script pack id '{}' is invalid, sanitized to '{}' ({})", manifest.id(), id, dir);
            }
            boolean duplicate = false;
            for (ScriptPack existing : packs) {
                if (existing.id().equals(id)) {
                    duplicate = manifest.descriptor() == null && existing.manifest().descriptor() == null;
                    NekoJS.LOGGER.warn("[NEKO-1016] Duplicate package identity detected; pack={}, previous={}, legacyDuplicateSkipped={}",
                            dir, existing.root(), duplicate);
                    break;
                }
            }
            if (duplicate) continue;
            ScriptPackState state = forceEnabled ? null : ScriptPackState.load(dir);
            boolean enabled = forceEnabled || (state != null ? state.enabled() : manifest.enabledByDefault());
            packs.add(new ScriptPack(id, manifest.name(), manifest.version(), scope, dir, enabled, manifest));
        }
        packs.sort(Comparator.comparing(ScriptPack::id));
        if (!packs.isEmpty()) {
            NekoJS.LOGGER.info("Discovered {} {} script pack(s) at {}", packs.size(), scope, root);
        }
        return packs;
    }

    private static List<ScriptPack> scanForceEnabled(Path root, ScriptPackScope scope) {
        return scan(root, scope, true);
    }

    private static List<ScriptPack> scanForceEnabled(Path root, ScriptPackScope scope,
                                                      Collection<String> activePackIds) {
        Set<String> selectedDirs = new HashSet<>();
        if (activePackIds != null) {
            for (String activePackId : activePackIds) {
                if (activePackId != null) {
                    selectedDirs.add(SyncedPack.encodeSyncId(activePackId));
                }
            }
        }
        return scan(root, scope, true, selectedDirs);
    }
}
