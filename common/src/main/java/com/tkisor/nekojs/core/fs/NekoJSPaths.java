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
 * Singleton for NekoJS file paths: get the global instance through {@link #get()}.
 *
 * <p>Immutable: every path is fixed once the instance is created. Instance methods return paths;
 * static methods run the checks.
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

    /* ================= Instance state ================= */
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
        // On Windows, gameDir can carry an 8.3 short name (such as C:\Users\RUNNER~1\...) or a
        // letter case that differs from the form on disk. Mixing those with toRealPath() results
        // makes every startsWith check fail. Normalize to the real form in the constructor so all
        // derived paths (root/scripts/config...) share the same base; if the real form cannot be
        // taken, fall back to the logical form.
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
        // The engine config sits next to probe.toml at <gamedir>/nekojs/config/engine.toml: all
        // NekoJS configuration lives under nekojs/config/, beside the script directories, which
        // makes packaging and lookup easier. The old location
        // <gamedir>/config/nekojs-engine.toml is a read-only fallback only (see
        // legacyEngineConfig).
        this.engineConfig = config.resolve("engine.toml");
        this.legacyEngineConfig = this.gameDir.resolve("config").resolve("nekojs-engine.toml");
        this.probeConfig = config.resolve("probe.toml");
        this.assets = root.resolve("assets");
        this.data = root.resolve("data");
        this.packsRoot = root.resolve(ScriptPackRegistry.GLOBAL_PACKS_DIR);
        this.serverPacks = root.resolve("server_packs");
        this.scriptRoots = Set.of(startupScripts, serverScripts, clientScripts, testScripts);
    }

    /* ================= Instance getters ================= */
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
    /** Old engine config location {@code <gamedir>/config/nekojs-engine.toml} (read-only migration fallback only). */
    public Path legacyEngineConfig() { return legacyEngineConfig; }
    public Path probeConfig() { return probeConfig; }
    public Path assets() { return assets; }
    public Path data() { return data; }
    /** Script pack root {@code <gamedir>/nekojs/packs/} (WORLD packs live with the save, not here). */
    public Path packsRoot() { return packsRoot; }
    /**
     * Client cache of script packs sent by a server: {@code <gamedir>/nekojs/server_packs/},
     * bucketed by a hash of the server address.
     */
    public Path serverPacks() { return serverPacks; }

    /* ================= Instance path setup ================= */
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

    /* ================= Path checks (instance methods) ================= */

    /**
     * Common canonical form of a path (see {@code canonicalSplicedForm}): toRealPath() when the
     * path exists, otherwise the real form of the nearest existing ancestor with the missing
     * suffix appended. Callers use it to compare and relativize paths the same way.
     */
    public static Path canonicalForm(Path path) {
        return canonicalSplicedForm(path);
    }

    /**
     * Checks whether a path lies inside the root given by the caller; both sides use the canonical
     * comparison form (existing -> toRealPath, missing -> real form of the nearest existing
     * ancestor plus the missing suffix). When the caller holds a root that is not canonical (a
     * temp directory with an 8.3 short name or a letter-case alias), a plain startsWith against
     * the canonical form returned by {@link #verifyInsideGameDir} and the other checks would miss.
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


    public boolean isInsideScriptRoot(Path path) {
        Path normalized = canonicalSplicedForm(path);
        return scriptRoots.stream()
                .map(rt -> canonicalSplicedForm(rt.normalize().toAbsolutePath()))
                .anyMatch(normalized::startsWith);
    }

    public boolean isSupportedScriptFile(Path path) {
        return ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(path);
    }

    /* ================= Internal helpers ================= */

    /**
     * Returns the toRealPath form when the path exists (resolving 8.3 short names, letter case
     * and symlinks); otherwise returns the path unchanged.
     */
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
     * Canonical comparison form of a path: {@code toRealPath()} when the path exists; otherwise
     * the real form of the nearest existing ancestor with the missing suffix appended. Missing
     * directories (such as {@code nekojs/} before initFolders) and missing target paths are built
     * by the same rule, so comparisons do not fail on 8.3 short name or letter-case spelling
     * differences.
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
     * Checks that a path stays inside a root. The ForCreate and plain variants share the same
     * physical containment rule:
     * <ul>
     *   <li>Target exists: decide and return using the real form from {@code toRealPath()}. This
     *       catches symlink escapes and 8.3 short name or letter-case mismatches alike (on
     *       Windows, mixing {@code C:\Users\RUNNER~1\...} with the long name that toRealPath
     *       resolves to is the usual source of false judgements);</li>
     *   <li>Target missing: decide using the spliced form, the real form of the nearest existing
     *       ancestor (resolving all symlinks and short names) plus the missing suffix. A symlink
     *       parent in the existing prefix that escapes is caught here, and a new path spelled
     *       through an alias is not rejected by mistake because both sides are spliced the same
     *       way. Returns that canonical form, so one logical path gives the same result no matter
     *       how the caller spells it.</li>
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
                // Logically inside the root, but the existing prefix resolves outside it: a symlink
                // ancestor escape
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
