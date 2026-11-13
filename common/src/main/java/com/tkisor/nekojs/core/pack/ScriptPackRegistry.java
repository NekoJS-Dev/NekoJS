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
 * Registry of script packs. It holds three scanned sets: GLOBAL ({@code nekojs/packs/}), WORLD
 * ({@code <world>/nekojs_packs/}, activated and unloaded by the platform when the server starts and
 * stops) and SERVER_CACHE ({@code nekojs/server_packs/<bucket>/}, activated by the client once
 * multiplayer pack distribution passes signature verification and trust, and unloaded on
 * disconnect). {@code ScriptLocator} merges them in when it discovers scripts.
 *
 * <p>Instances can be constructed (tests inject a temporary directory); the static default instance
 * follows {@link NekoJSPaths}. GLOBAL packs are scanned lazily once on the first access in the
 * process and updated afterwards only by {@link #refreshGlobalPacks()} (the reload command calls it
 * before script discovery, so an enabled-state change takes effect immediately).
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

    /**
     * All enabled packs in script load order: GLOBAL, then WORLD, then SERVER_CACHE, alphabetical
     * within each.
     */
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

    /** Server cache packs that are currently active (multiplayer pack distribution, called on the client). */
    public List<ScriptPack> serverCachePacks() {
        return serverCachePacks;
    }

    /** Scans global packs on the first call and does nothing on later calls. */
    private void refreshGlobalPacksOnce() {
        if (globalScanned) return;
        synchronized (this) {
            if (globalScanned) return;
            refreshGlobalPacks();
        }
    }

    /** Rescans global packs; called by the reload command and after packs are switched. */
    public synchronized void refreshGlobalPacks() {
        refreshGlobalPacks(NekoJSPaths.get().packsRoot());
    }

    /** For tests and injection: rescans global packs under the given root directory. */
    public synchronized void refreshGlobalPacks(Path packsRoot) {
        globalRoot = packsRoot;
        globalScanned = true;
        globalPacks = scan(packsRoot, ScriptPackScope.GLOBAL);
    }

    /**
     * Activates world packs: scans {@code worldDir/nekojs_packs/} and replaces the current world pack
     * set. Returns all WORLD packs activated by this call, disabled ones included, for list commands.
     *
     * <p>worldDir is normalized to an absolute path first. Platform entry points may pass a relative
     * form (a Windows dedicated server was observed passing {@code .\world\.}) while the nekojs root
     * is always absolute. Keeping pack script paths relative then makes the later
     * {@code root.relativize(script.path)} mix absolute and relative inputs, throw IAE, and fail every
     * WORLD pack script.
     */
    public synchronized List<ScriptPack> activateWorldPacks(Path worldDir) {
        worldSession++;
        worldRoot = worldDir == null ? null : worldDir.toAbsolutePath().normalize().resolve(WORLD_PACKS_DIR);
        worldPacks = worldRoot == null
                ? List.of()
                : scan(worldRoot, ScriptPackScope.WORLD);
        return worldPacks;
    }

    /** Unloads world packs (serverStopped or leaving single player) and returns the removed packs. */
    public synchronized List<ScriptPack> deactivateWorldPacks() {
        worldSession++;
        List<ScriptPack> removed = worldPacks;
        worldPacks = List.of();
        worldRoot = null;
        return removed;
    }

    /**
     * Activates server cache packs on the client for multiplayer distribution: scans
     * {@code nekojs/server_packs/<bucket>/} and replaces the current SERVER_CACHE set. Server cache
     * packs are forced enabled; neither the manifest default nor the state file applies. Returns all
     * activated packs.
     */
    public synchronized List<ScriptPack> activateServerCachePacks(Path bucketDir) {
        updateCacheSession(bucketDir);
        serverCachePacks = bucketDir == null
            ? List.of()
            : scanForceEnabled(bucketDir, ScriptPackScope.SERVER_CACHE);
        return serverCachePacks;
    }

    /**
     * Activates the server cache packs listed in the given sync manifest. Old directories in the cache
     * bucket that are not selected are not scanned; their files stay on disk. {@code activePackIds}
     * uses the transport syncId (for example {@code packs:demo}), not the manifest id.
     */
    public synchronized List<ScriptPack> activateServerCachePacks(Path bucketDir,
                                                                    Collection<String> activePackIds) {
        updateCacheSession(bucketDir);
        serverCachePacks = bucketDir == null
            ? List.of()
            : scanForceEnabled(bucketDir, ScriptPackScope.SERVER_CACHE, activePackIds);
        return serverCachePacks;
    }

    /**
     * Unloads server cache packs (disconnect, or the server clears the pack set) and returns the removed
     * packs. Cached files stay on disk.
     */
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

    /** Resets the scan flags; for tests, so the next get() scans lazily again. */
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

    /**
     * Scans every subdirectory that has a manifest.json as a pack. Corrupt manifests are skipped, and on
     * a duplicate id in the same scope the later pack is skipped.
     */
    private static List<ScriptPack> scan(Path root, ScriptPackScope scope) {
        return scan(root, scope, false, null);
    }

    /**
     * Scans every subdirectory that has a manifest.json as a pack. Corrupt manifests are skipped, and
     * on a duplicate id in the same scope the later pack is skipped.
     *
     * @param forceEnabled true ignores the manifest default and the state file and forces the
     *                     pack enabled (SERVER_CACHE semantics: verification and trust decide whether
     *                     it runs, so the in-pack enable switch does not apply)
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
            if (manifest == null) continue; // No manifest (not a pack) or a corrupt one (already warned about)
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
