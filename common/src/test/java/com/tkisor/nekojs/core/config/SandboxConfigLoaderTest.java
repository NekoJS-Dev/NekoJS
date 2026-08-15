package com.tkisor.nekojs.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SandboxConfigLoader} 语义校验：默认值（含语句上限 5e7）、显式值（含 0）优先、
 * 损坏 TOML 回退默认配置、废弃旧键清理。
 */
class SandboxConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileYieldsDefaultsIncludingStatementLimit() {
        Path engineConfig = tempDir.resolve("nekojs-engine.toml");

        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);

        assertEquals(SandboxConfig.defaultConfig(), loaded);
        assertEquals(0L, loaded.scriptStatementLimit());
        assertEquals(SandboxConfig.DEFAULT_SCRIPT_RUNAWAY_TIMEOUT_SECONDS, loaded.scriptRunawayTimeoutSeconds());
        assertFalse(loaded.allowThreads());
        assertFalse(loaded.allowReflection());
        assertFalse(loaded.allowAsm());
        assertFalse(loaded.allowFsWriteOutsideNekojs());
        assertTrue(loaded.enableEsmAuthoring());
        assertTrue(loaded.conciseScriptErrorLogs());
        assertFalse(loaded.jsxAutomaticRuntime());
        assertTrue(loaded.scriptMemberValidation());
        assertEquals(30, loaded.scriptEvaluationTimeoutSeconds());
    }

    @Test
    void explicitStatementLimitZeroIsHonored() {
        Path engineConfig = writeEngineConfig("""
                scriptStatementLimit = 0
                """);

        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);

        assertEquals(0L, loaded.scriptStatementLimit());
    }

    @Test
    void explicitStatementLimitValueIsHonored() {
        Path engineConfig = writeEngineConfig("""
                scriptStatementLimit = 1234567
                """);

        assertEquals(1234567L, new SandboxConfigLoader().load(engineConfig).scriptStatementLimit());
    }

    @Test
    void corruptTomlFallsBackToDefaultConfig() throws Exception {
        Path engineConfig = tempDir.resolve("nekojs-engine.toml");
        Files.writeString(engineConfig, "this is not valid toml [[[\n", StandardCharsets.UTF_8);

        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);

        assertEquals(SandboxConfig.defaultConfig(), loaded);
        assertEquals(0L, loaded.scriptStatementLimit());
    }

    @Test
    void explicitRunawayTimeoutIsHonored() {
        Path engineConfig = writeEngineConfig("""
                scriptRunawayTimeoutSeconds = 25
                scriptStatementLimit = 123456
                """);

        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);

        assertEquals(25, loaded.scriptRunawayTimeoutSeconds());
        assertEquals(123456L, loaded.scriptStatementLimit());
    }

    @Test
    void runawayTimeoutZeroMeansDisabled() {
        Path engineConfig = writeEngineConfig("""
                scriptRunawayTimeoutSeconds = 0
                """);

        assertEquals(0, new SandboxConfigLoader().load(engineConfig).scriptRunawayTimeoutSeconds());
    }

    @Test
    void removesLegacyKeysButKeepsExplicitUnsafeFlags() throws Exception {
        Path engineConfig = writeEngineConfig("""
                prependRequirePatch = true
                useNekoScriptLoader = true
                useNativeEsmLoader = true
                allowThreads = true
                allowFsWriteOutsideNekojs = true
                scriptStatementLimit = 0
                """);

        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);

        // 废弃键被移除，显式高危开关保留
        assertTrue(loaded.allowThreads());
        assertTrue(loaded.allowFsWriteOutsideNekojs());
        assertTrue(loaded.anyUnsafeFeatureEnabled());
        assertEquals(0L, loaded.scriptStatementLimit());

        String persisted = Files.readString(engineConfig, StandardCharsets.UTF_8);
        assertFalse(persisted.contains("prependRequirePatch"), "legacy key should be removed from file");
        assertFalse(persisted.contains("useNekoScriptLoader"), "legacy key should be removed from file");
        assertFalse(persisted.contains("useNativeEsmLoader"), "legacy key should be removed from file");
    }

    @Test
    void readOnlyLoadLeavesFileUntouchedButAppliesDefaultsInMemory() throws Exception {
        // 模拟旧位置 engine.toml：含废弃键、缺少大部分新键、带用户显式值
        String legacyContent = """


                prependRequirePatch = true
                allowThreads = true
                scriptStatementLimit = 0
                """;
        Path legacyConfig = writeEngineConfig(legacyContent);

        SandboxConfig loaded = new SandboxConfigLoader().load(legacyConfig, false);

        // 内存中：默认值生效、用户显式值保留
        assertEquals(0L, loaded.scriptStatementLimit());
        assertTrue(loaded.allowThreads());
        assertTrue(loaded.enableEsmAuthoring());
        assertFalse(loaded.allowReflection());

        // 磁盘上：文件一字不动（只读回退契约——不补键、不清理废弃键）
        assertEquals(legacyContent, Files.readString(legacyConfig, StandardCharsets.UTF_8));
    }

    @Test
    void fsWriteOutsideNekojsAloneCountsAsUnsafeFeature() {
        SandboxConfig fsWriteOnly = new SandboxConfig(false, false, false, true, true, true, false, true, 30, 50_000_000L, 10);
        assertTrue(fsWriteOnly.anyUnsafeFeatureEnabled());
        assertFalse(SandboxConfig.defaultConfig().anyUnsafeFeatureEnabled());
    }

    private Path writeEngineConfig(String toml) {
        try {
            Path engineConfig = tempDir.resolve("nekojs-engine.toml");
            Files.writeString(engineConfig, toml, StandardCharsets.UTF_8);
            return engineConfig;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
