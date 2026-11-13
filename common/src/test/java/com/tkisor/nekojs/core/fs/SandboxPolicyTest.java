package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.config.SandboxConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SandboxPolicy} is the single decision point for writes to the two-layer script
 * filesystem (Graal FileSystem and the Node shim): protected config files are refused, the flags
 * converge, and both layers agree.
 */
class SandboxPolicyTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths() throws IOException {
        Files.createDirectories(gameDir.resolve("nekojs/config"));
        return NekoJSPaths.fromGameDir(gameDir);
    }

    @Test
    void engineConfigDirectoryIsAlwaysWriteProtected() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);

        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWrite(paths.config().resolve("engine.toml")));
        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWriteExisting(paths.engineConfig()));
        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWrite(paths.probeConfig()));
        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWrite(paths.config().resolve("subdir/anything.toml")));
    }

    @Test
    void legacyEngineConfigFileIsWriteProtected() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);
        // Strict mode: the path itself is outside the nekojs root, so the scope check rejects it first (IOException).
        assertThrows(IOException.class, () -> strict.resolveWrite(paths.legacyEngineConfig()));
    }

    @Test
    void strictModeConfinesWritesToNekoRoot() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);

        // Allowed inside the nekojs root; the for-create form of a path that does not exist must pass too.
        assertDoesNotThrow(() -> strict.resolveWrite(paths.root().resolve("startup_scripts/new.js")));
        // Rejected inside gameDir but outside the nekojs root (the Graal FS used to allow mods/ unconditionally).
        assertThrows(IOException.class, () -> strict.resolveWrite(gameDir.resolve("mods/malicious.jar")));
        assertThrows(IOException.class, () -> strict.resolveWrite(gameDir.resolve("world/data/x.dat")));
    }

    @Test
    void looseModeAllowsGameDirButStillProtectsConfig() throws IOException {
        NekoJSPaths paths = paths();
        SandboxConfig def = SandboxConfig.defaultConfig();
        SandboxConfig loose = new SandboxConfig(
                def.allowThreads(), def.allowReflection(), def.allowAsm(), true,
                def.enableEsmAuthoring(), def.conciseScriptErrorLogs(), def.jsxAutomaticRuntime(),
                def.scriptMemberValidation(),
                def.packSyncMode(), def.packSyncAllowUnsigned(), def.dynamicRegistryEnabled(), def.mixinJsEnabled());
        SandboxPolicy policy = new SandboxPolicy(loose, paths);

        assertDoesNotThrow(() -> policy.resolveWrite(gameDir.resolve("somewhere/output.txt")));
        assertThrows(AccessDeniedException.class,
                () -> policy.resolveWrite(paths.config().resolve("engine.toml")));
        // In lenient mode the old location is inside the scope, so the config protection must stop it
        // (AccessDenied rather than allowed).
        assertThrows(AccessDeniedException.class,
                () -> policy.resolveWrite(paths.legacyEngineConfig()));
    }

    @Test
    void nodeShapedStringResolutionMatchesPathResolution() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);

        // The Node shim's string entry point: absolute and relative forms (relative uses gameDir as the
        // working directory) are judged the same way.
        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWrite("nekojs/config/engine.toml", gameDir));
        assertThrows(IOException.class,
                () -> strict.resolveWrite("mods/malicious.jar", gameDir));
        assertDoesNotThrow(() -> strict.resolveWrite("nekojs/out.txt", gameDir));
    }
}
