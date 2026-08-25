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
 * {@link SandboxPolicy} 是两层脚本文件系统（Graal FileSystem + Node shim）写裁决的唯一真相
 * （W7/A6，审计 §3-23/24）：受保护配置拒写、flag 收敛、两层一致。
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
        // 严格模式：路径本身就在 neko 根外，先被作用域拒绝（IOException）
        assertThrows(IOException.class, () -> strict.resolveWrite(paths.legacyEngineConfig()));
    }

    @Test
    void strictModeConfinesWritesToNekoRoot() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);

        // neko 根内放行（不存在路径的 for-create 形式也应通过）
        assertDoesNotThrow(() -> strict.resolveWrite(paths.root().resolve("startup_scripts/new.js")));
        // gameDir 内、neko 根外拒绝（§3-24：此前 Graal FS 无条件放行到 mods/）
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
                def.scriptMemberValidation(), def.scriptEvaluationTimeoutSeconds(),
                def.scriptStatementLimit(), def.scriptRunawayTimeoutSeconds(),
                def.packSyncMode(), def.packSyncAllowUnsigned(), def.dynamicRegistryEnabled());
        SandboxPolicy policy = new SandboxPolicy(loose, paths);

        assertDoesNotThrow(() -> policy.resolveWrite(gameDir.resolve("somewhere/output.txt")));
        assertThrows(AccessDeniedException.class,
                () -> policy.resolveWrite(paths.config().resolve("engine.toml")));
        // 宽松模式下旧位置在作用域内，必须由配置保护挡下（AccessDenied 而非放行）
        assertThrows(AccessDeniedException.class,
                () -> policy.resolveWrite(paths.legacyEngineConfig()));
    }

    @Test
    void nodeShapedStringResolutionMatchesPathResolution() throws IOException {
        NekoJSPaths paths = paths();
        SandboxPolicy strict = new SandboxPolicy(SandboxConfig.defaultConfig(), paths);

        // Node shim 的字符串入口：绝对/相对（相对 gameDir 工作目录）两种形式同判
        assertThrows(AccessDeniedException.class,
                () -> strict.resolveWrite("nekojs/config/engine.toml", gameDir));
        assertThrows(IOException.class,
                () -> strict.resolveWrite("mods/malicious.jar", gameDir));
        assertDoesNotThrow(() -> strict.resolveWrite("nekojs/out.txt", gameDir));
    }
}
