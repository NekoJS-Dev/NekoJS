package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 脚本包注册表：持有 GLOBAL（{@code nekojs/packs/}）与 WORLD（{@code <world>/nekojs_packs/}，
 * 由平台在服务器启动/停止时激活/卸载）两批已扫描包，供 {@code ScriptLocator} 发现脚本时并入。
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
    private volatile boolean globalScanned;

    /** 全部启用包：GLOBAL（字母序）在前、WORLD（字母序）在后，与脚本加载顺序一致。 */
    public List<ScriptPack> enabledPacks() {
        List<ScriptPack> out = new ArrayList<>(globalPacks.size() + worldPacks.size());
        for (ScriptPack pack : globalPacks) if (pack.enabled()) out.add(pack);
        for (ScriptPack pack : worldPacks) if (pack.enabled()) out.add(pack);
        return out;
    }

    public List<ScriptPack> globalPacks() {
        return globalPacks;
    }

    public List<ScriptPack> worldPacks() {
        return worldPacks;
    }

    /** 首次调用时扫描一次全局包；幂等。 */
    private void refreshGlobalPacksOnce() {
        if (globalScanned) return;
        synchronized (this) {
            if (globalScanned) return;
            globalPacks = scan(NekoJSPaths.get().root().resolve(GLOBAL_PACKS_DIR), ScriptPackScope.GLOBAL);
            globalScanned = true;
        }
    }

    /** 重扫全局包（reload 命令 / 包切换后调用）。 */
    public synchronized void refreshGlobalPacks() {
        globalScanned = true;
        globalPacks = scan(NekoJSPaths.get().root().resolve(GLOBAL_PACKS_DIR), ScriptPackScope.GLOBAL);
    }

    /** 测试/注入用：按指定根目录重扫全局包。 */
    public synchronized void refreshGlobalPacks(Path packsRoot) {
        globalScanned = true;
        globalPacks = scan(packsRoot, ScriptPackScope.GLOBAL);
    }

    /**
     * 激活世界包：扫描 {@code worldDir/nekojs_packs/} 并替换当前世界包集合。
     * 返回本次激活的全部 WORLD 包（含禁用者，供列表命令展示）。
     */
    public synchronized List<ScriptPack> activateWorldPacks(Path worldDir) {
        worldPacks = worldDir == null
            ? List.of()
            : scan(worldDir.resolve(WORLD_PACKS_DIR), ScriptPackScope.WORLD);
        return worldPacks;
    }

    /** 卸载世界包（serverStopped / 退出单人），返回被移除的包。 */
    public synchronized List<ScriptPack> deactivateWorldPacks() {
        List<ScriptPack> removed = worldPacks;
        worldPacks = List.of();
        return removed;
    }

    /** 重置扫描标记（测试用：让下一次 get() 重新懒扫描）。 */
    synchronized void resetForTest() {
        globalScanned = false;
        globalPacks = List.of();
        worldPacks = List.of();
    }

    /** 扫描目录下所有含 manifest.json 的子目录为包；损坏 manifest 跳过，同 scope 重复 id 后者跳过。 */
    private static List<ScriptPack> scan(Path root, ScriptPackScope scope) {
        if (root == null || !Files.isDirectory(root)) return List.of();
        List<Path> dirs;
        try (Stream<Path> stream = Files.list(root)) {
            dirs = stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to list script pack directory {}: {}", root, e.toString());
            return List.of();
        }
        List<ScriptPack> packs = new ArrayList<>();
        for (Path dir : dirs) {
            String fallbackId = ScriptPackManifest.sanitizeId(dir.getFileName().toString());
            ScriptPackManifest manifest = ScriptPackManifest.load(dir, fallbackId);
            if (manifest == null) continue; // 无 manifest（非包）或损坏（已 WARN）
            String id = ScriptPackManifest.sanitizeId(manifest.id());
            if (!id.equals(manifest.id())) {
                NekoJS.LOGGER.warn("Script pack id '{}' is invalid, sanitized to '{}' ({})", manifest.id(), id, dir);
            }
            boolean duplicate = false;
            for (ScriptPack existing : packs) {
                if (existing.id().equals(id)) {
                    NekoJS.LOGGER.warn("Duplicate script pack id '{}' in {}, keeping {} (skipped)", id, dir, existing.root());
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            ScriptPackState state = ScriptPackState.load(dir);
            boolean enabled = state != null ? state.enabled() : manifest.enabledByDefault();
            packs.add(new ScriptPack(id, manifest.name(), manifest.version(), scope, dir, enabled, manifest));
        }
        packs.sort(Comparator.comparing(ScriptPack::id));
        if (!packs.isEmpty()) {
            NekoJS.LOGGER.info("Discovered {} {} script pack(s) at {}", packs.size(), scope, root);
        }
        return packs;
    }
}
