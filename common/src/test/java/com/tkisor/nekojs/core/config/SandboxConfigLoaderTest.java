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
        assertEquals(50_000_000L, loaded.scriptStatementLimit());
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
        assertEquals(50_000_000L, loaded.scriptStatementLimit());
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
    void fsWriteOutsideNekojsAloneCountsAsUnsafeFeature() {
        SandboxConfig fsWriteOnly = new SandboxConfig(false, false, false, true, true, true, false, true, 30, 50_000_000L);
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
