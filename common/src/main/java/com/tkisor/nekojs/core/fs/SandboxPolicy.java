package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.config.SandboxConfig;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;

/**
 * Single source of truth for the filesystem write policy that scripts can reach.
 *
 * <p>Both the Graal {@link NekoJSFileSystem} and the Node shim ({@code NekoNodeFS}) decide every
 * write and delete here. The protected directory {@code nekojs/config/} holds the
 * {@code engine.toml} that defines the sandbox itself: a script that set
 * {@code allowReflection = true} there would remove the sandbox on the next start.
 *
 * <p>The rules:
 * <ol>
 *   <li>The whole {@code nekojs/config/} directory and the old location
 *       {@code <gameDir>/config/nekojs-engine.toml} always reject writes and deletes: code inside
 *       the sandbox must not rewrite the files that define it. Host-side config materialization
 *       and probe editor files go straight through {@link java.nio.file.Files} and do not pass
 *       through this class.</li>
 *   <li>{@code allowFsWriteOutsideNekojs=false}: writes and deletes are only allowed inside the
 *       neko root; {@code true}: also inside gameDir (the protected config is still
 *       rejected).</li>
 * </ol>
 */
public final class SandboxPolicy {

    private final NekoJSPaths paths;
    private final boolean allowWriteOutsideNekojs;
    private final Path protectedConfigDir;
    private final Path protectedLegacyEngineConfig;

    public SandboxPolicy(SandboxConfig config, NekoJSPaths paths) {
        this.paths = paths;
        this.allowWriteOutsideNekojs = config.allowFsWriteOutsideNekojs();
        this.protectedConfigDir = NekoJSPaths.canonicalForm(paths.config());
        this.protectedLegacyEngineConfig = NekoJSPaths.canonicalForm(paths.legacyEngineConfig());
    }

    /**
     * Decides a write target (create or overwrite). Used by the Graal FileSystem layer, where the
     * path is already a resolved absolute path.
     *
     * @throws AccessDeniedException the path leaves the writable scope, or hits the protected
     *                               engine config
     */
    public Path resolveWrite(Path path) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.verifyInsideGameDirForCreate(path)
                : paths.verifyInsideNekoRootForCreate(path);
        return rejectProtected(target);
    }

    /** Decides a delete or rename target (existing-path variant). */
    public Path resolveWriteExisting(Path path) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.verifyInsideGameDir(path)
                : paths.verifyInsideNekoRoot(path);
        return rejectProtected(target);
    }

    /**
     * Decides a write target for the Node shim layer: a raw string, resolved against the current
     * working directory.
     */
    public Path resolveWrite(String path, Path currentWorkingDirectory) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.resolveGamePathForCreate(path, currentWorkingDirectory)
                : paths.resolveNekoWritePathForCreate(path, currentWorkingDirectory);
        return rejectProtected(target);
    }

    /** Decides a delete or rename target for the Node shim layer (existing-path variant). */
    public Path resolveWriteExisting(String path, Path currentWorkingDirectory) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.resolveGamePath(path, currentWorkingDirectory)
                : paths.resolveNekoWritePath(path, currentWorkingDirectory);
        return rejectProtected(target);
    }

    /**
     * Whether the path is a file that defines the sandbox itself: the nekojs/config/ directory or
     * the single legacy engine config file.
     */
    public boolean isProtectedConfigPath(Path path) {
        Path canonical = NekoJSPaths.canonicalForm(path);
        return canonical.startsWith(protectedConfigDir) || canonical.equals(protectedLegacyEngineConfig);
    }

    private Path rejectProtected(Path target) throws AccessDeniedException {
        if (isProtectedConfigPath(target)) {
            throw new AccessDeniedException(target.toString(), null,
                    "engine configuration files cannot be modified from scripts (they define the sandbox itself)");
        }
        return target;
    }
}
