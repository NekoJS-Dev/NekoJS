package com.tkisor.nekojs.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies current engine settings, retired-key migration, and the read-only startup MixinJS opt-in. */
class SandboxConfigLoaderTest {
    @TempDir Path tempDir;

    @Test
    void missingFileCreatesOnlyRetainedDefaults() throws Exception {
        Path engineConfig = tempDir.resolve("nekojs-engine.toml");
        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);
        assertEquals(SandboxConfig.defaultConfig(), loaded);
        assertFalse(loaded.allowThreads());
        assertFalse(loaded.allowReflection());
        assertFalse(loaded.allowAsm());
        assertFalse(loaded.allowFsWriteOutsideNekojs());
        assertTrue(loaded.enableEsmAuthoring());
        assertTrue(loaded.conciseScriptErrorLogs());
        assertFalse(loaded.jsxAutomaticRuntime());
        assertTrue(loaded.scriptMemberValidation());
        assertFalse(loaded.mixinJsEnabled());
        assertNoRetiredKeys(Files.readString(engineConfig, StandardCharsets.UTF_8));
    }

    @Test
    void retiredResourceControlsAreRemovedRegardlessOfTheirFormerTypes() throws Exception {
        Path engineConfig = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(engineConfig.getParent());
        for (String formerValue : java.util.List.of("17", "-1", "\"obsolete\"", "[]", "true")) {
            String source = "scriptEvaluationTimeoutSeconds = " + formerValue + "\n"
                    + "scriptStatementLimit = " + formerValue + "\n"
                    + "scriptRunawayTimeoutSeconds = " + formerValue + "\n"
                    + "allowThreads = true\nallowFsWriteOutsideNekojs = true\n"
                    + "[packSync]\nmode = \"all\"\nallowUnsigned = true\n"
                    + "[dynamicRegistry]\nenabled = true\n[mixinjs]\nenabled = true\n";
            Files.writeString(engineConfig, source, StandardCharsets.UTF_8);
            SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);
            assertTrue(loaded.allowThreads());
            assertTrue(loaded.allowFsWriteOutsideNekojs());
            assertEquals(SandboxConfig.PACK_SYNC_ALL, loaded.packSyncMode());
            assertTrue(loaded.packSyncAllowUnsigned());
            assertTrue(loaded.dynamicRegistryEnabled());
            assertTrue(loaded.mixinJsEnabled());
            assertTrue(SandboxConfigLoader.readMixinJsEnabled(tempDir));
            assertNoRetiredKeys(Files.readString(engineConfig, StandardCharsets.UTF_8));
        }
    }

    @Test
    void readOnlyLegacyMigrationLeavesEveryOriginalByteUntouched() throws Exception {
        String source = "# Keep this legacy file byte-for-byte.\nprependRequirePatch = true\n"
                + "scriptEvaluationTimeoutSeconds = -1\nscriptStatementLimit = \"retired\"\n"
                + "scriptRunawayTimeoutSeconds = []\nallowThreads = true\n[mixinjs]\nenabled = true\n";
        Path legacy = writeEngineConfig(source);
        byte[] before = Files.readAllBytes(legacy);
        SandboxConfig loaded = new SandboxConfigLoader().load(legacy, false);
        assertTrue(loaded.allowThreads());
        assertTrue(loaded.enableEsmAuthoring());
        assertFalse(loaded.allowReflection());
        assertTrue(loaded.mixinJsEnabled());
        assertArrayEquals(before, Files.readAllBytes(legacy));
    }

    @Test
    void currentModelExposesOnlyRetainedComponentsAndConstructors() {
        var components = java.util.Arrays.stream(SandboxConfig.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of("allowThreads", "allowReflection", "allowAsm", "allowFsWriteOutsideNekojs",
                "enableEsmAuthoring", "conciseScriptErrorLogs", "jsxAutomaticRuntime", "scriptMemberValidation",
                "packSyncMode", "packSyncAllowUnsigned", "dynamicRegistryEnabled", "mixinJsEnabled"), components);
        var arities = java.util.Arrays.stream(SandboxConfig.class.getConstructors())
                .map(java.lang.reflect.Constructor::getParameterCount).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(8, 11, 12), arities);
    }

    @Test
    void corruptTomlFallsBackToDefaultConfig() throws Exception {
        Path engineConfig = writeEngineConfig("this is not valid toml [[[\n");
        assertEquals(SandboxConfig.defaultConfig(), new SandboxConfigLoader().load(engineConfig));
    }

    @Test
    void removesLegacyKeysButKeepsExplicitUnsafeFlags() throws Exception {
        Path engineConfig = writeEngineConfig("prependRequirePatch = true\nuseNekoScriptLoader = true\n"
                + "useNativeEsmLoader = true\nallowThreads = true\nallowFsWriteOutsideNekojs = true\n");
        SandboxConfig loaded = new SandboxConfigLoader().load(engineConfig);
        assertTrue(loaded.allowThreads());
        assertTrue(loaded.allowFsWriteOutsideNekojs());
        assertTrue(loaded.anyUnsafeFeatureEnabled());
        String persisted = Files.readString(engineConfig, StandardCharsets.UTF_8);
        assertFalse(persisted.contains("prependRequirePatch"));
        assertFalse(persisted.contains("useNekoScriptLoader"));
        assertFalse(persisted.contains("useNativeEsmLoader"));
    }

    @Test
    void fsWriteOutsideNekojsAloneCountsAsUnsafeFeature() {
        var fsWriteOnly = new SandboxConfig(false, false, false, true, true, true, false, true);
        assertTrue(fsWriteOnly.anyUnsafeFeatureEnabled());
        assertFalse(SandboxConfig.defaultConfig().anyUnsafeFeatureEnabled());
    }

    private static void assertNoRetiredKeys(String source) {
        for (String key : java.util.List.of("scriptEvaluationTimeoutSeconds", "scriptStatementLimit", "scriptRunawayTimeoutSeconds")) {
            assertFalse(source.contains(key), "Retired resource control must not be persisted: " + key);
        }
    }

    @Test
    void mixinJsIsDefaultOffAndPersistsTheCanonicalEngineKey() throws Exception {
        Path canonical = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(canonical.getParent());
        SandboxConfig loaded = new SandboxConfigLoader().load(canonical);
        assertFalse(loaded.mixinJsEnabled());
        assertFalse(SandboxConfig.defaultConfig().mixinJsEnabled());
        String persisted = Files.readString(canonical);
        assertTrue(persisted.contains("[mixinjs]"), persisted);
        String section = persisted.substring(persisted.indexOf("[mixinjs]"));
        section = section.split("\\R\\[", 2)[0];
        assertTrue(section.lines().anyMatch(line -> line.strip().equals("enabled = false")), persisted);
        assertFalse(SandboxConfigLoader.readMixinJsEnabled(tempDir));
    }

    @Test
    void supportedSandboxConstructorsKeepMixinJsDisabled() {
        var shortConfig = new SandboxConfig(false, false, false, false, true, true, false, true);
        var originalConfig = new SandboxConfig(false, false, false, false, true, true, false, true,
                SandboxConfig.PACK_SYNC_ALL, true, true);
        assertFalse(shortConfig.mixinJsEnabled());
        assertFalse(originalConfig.mixinJsEnabled());
        assertTrue(originalConfig.packSyncAllowUnsigned());
        assertTrue(originalConfig.dynamicRegistryEnabled());
    }

    @Test
    void earlyMixinJsLookupNeverCreatesMissingDirectoriesOrFiles() {
        Path absentGame = tempDir.resolve("not-created");
        assertFalse(SandboxConfigLoader.readMixinJsEnabled(absentGame));
        assertFalse(Files.exists(absentGame));
        assertFalse(SandboxConfigLoader.readMixinJsEnabled(tempDir));
        assertFalse(Files.exists(tempDir.resolve("nekojs")));
        assertFalse(Files.exists(tempDir.resolve("config")));
    }

    @Test
    void earlyMixinJsLookupHonorsLegacyReadOnlyOnlyWhenCanonicalIsAbsent() throws Exception {
        Path legacy = tempDir.resolve("config/nekojs-engine.toml");
        Files.createDirectories(legacy.getParent());
        String legacySource = "allowThreads = true\n[mixinjs]\nenabled = true\n";
        Files.writeString(legacy, legacySource, StandardCharsets.UTF_8);
        assertTrue(SandboxConfigLoader.readMixinJsEnabled(tempDir));
        assertTrue(new SandboxConfigLoader().load(legacy, false).mixinJsEnabled());
        assertEquals(legacySource, Files.readString(legacy, StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve("nekojs")));
        Path canonical = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(canonical.getParent());
        for (String source : java.util.List.of("[mixinjs]\nenabled = false\n", "allowThreads = true\n",
                "[mixinjs]\nenabled = \"true\"\n", "not valid [toml")) {
            Files.writeString(canonical, source, StandardCharsets.UTF_8);
            assertFalse(SandboxConfigLoader.readMixinJsEnabled(tempDir), source);
            assertEquals(source, Files.readString(canonical, StandardCharsets.UTF_8));
            assertEquals(legacySource, Files.readString(legacy, StandardCharsets.UTF_8));
        }
    }

    @Test
    void earlyAndNormalMixinJsReadsRequireAnActualBoolean() throws Exception {
        Path canonical = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(canonical.getParent());
        var cases = java.util.Map.of("true", true, "false", false, "\"true\"", false, "1", false, "[]", false);
        for (var entry : cases.entrySet()) {
            String source = "allowThreads = true\n[mixinjs]\nenabled = " + entry.getKey() + "\n";
            Files.writeString(canonical, source, StandardCharsets.UTF_8);
            var loaded = new SandboxConfigLoader().load(canonical, false);
            assertEquals(entry.getValue().booleanValue(), loaded.mixinJsEnabled(), source);
            assertEquals(entry.getValue().booleanValue(), SandboxConfigLoader.readMixinJsEnabled(tempDir), source);
            assertTrue(loaded.allowThreads(), "Reading MixinJS must not reset other sandbox settings");
            assertEquals(source, Files.readString(canonical, StandardCharsets.UTF_8));
        }
    }

    @Test
    void explicitMixinJsOptInKeepsOtherSettingsAndTheExistingUnsafePolicy() throws Exception {
        Path canonical = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(canonical.getParent());
        Files.writeString(canonical, "allowAsm = true\n[mixinjs]\nenabled = true\n", StandardCharsets.UTF_8);
        var loaded = new SandboxConfigLoader().load(canonical);
        assertTrue(loaded.mixinJsEnabled());
        assertTrue(loaded.allowAsm());
        assertFalse(loaded.allowReflection());
        assertFalse(loaded.anyUnsafeFeatureEnabled(), "MixinJS must not change the existing unsafe-warning policy");
        assertTrue(SandboxConfigLoader.readMixinJsEnabled(tempDir));
    }

    @Test
    void earlyMixinJsLookupFailsClosedForAnUnreadableCanonicalFileShape() throws Exception {
        Path canonical = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(canonical);
        Path legacy = tempDir.resolve("config/nekojs-engine.toml");
        Files.createDirectories(legacy.getParent());
        String source = "[mixinjs]\nenabled = true\n";
        Files.writeString(legacy, source, StandardCharsets.UTF_8);
        assertFalse(SandboxConfigLoader.readMixinJsEnabled(tempDir));
        assertTrue(Files.isDirectory(canonical));
        assertEquals(source, Files.readString(legacy, StandardCharsets.UTF_8));
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
