package com.tkisor.nekojs.core.config;

import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifies current engine settings, retired-key migration, and the read-only startup MixinJS opt-in. */
class SandboxConfigLoaderTest {
    @TempDir Path tempDir;

    @Test
    void missingFileCreatesOnlyRetainedDefaults() throws Exception {
        var paths = NekoJSPaths.fromGameDir(tempDir);
        SandboxConfig previous = ClassFilter.INSTANCE.config();
        SandboxConfig loaded;
        try {
            var settings = ClassFilter.loadEngineSettings(paths);
            assertEquals(paths.engineConfig(), settings.path());
            loaded = settings.config();
            assertSame(loaded, ClassFilter.INSTANCE.config());
        } finally {
            ClassFilter.INSTANCE.updateConfig(previous);
        }
        Path engineConfig = paths.engineConfig();
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
        assertTrue(loaded.languagePipelines().isEmpty());
        assertFalse(Files.exists(paths.legacyEngineConfig()));
        assertNoRetiredKeys(Files.readString(engineConfig, StandardCharsets.UTF_8));
    }

    @Test
    void retiredResourceControlsAreRemovedRegardlessOfTheirFormerTypes() throws Exception {
        Path engineConfig = tempDir.resolve("nekojs/config/engine.toml");
        Files.createDirectories(engineConfig.getParent());
        for (String formerValue : List.of("17", "-1", "\"obsolete\"", "[]", "true")) {
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
                "packSyncMode", "packSyncAllowUnsigned", "dynamicRegistryEnabled", "mixinJsEnabled", "languagePipelines"), components);
        var arities = java.util.Arrays.stream(SandboxConfig.class.getConstructors())
                .map(java.lang.reflect.Constructor::getParameterCount).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(8, 11, 12, 13), arities);
    }

    @Test
    void canonicalRoundTripPreservesLiteralExtensionsAndIndependentStageSelections() throws Exception {
        var paths = NekoJSPaths.fromGameDir(tempDir);
        Path canonical = paths.engineConfig();
        Files.createDirectories(canonical.getParent());
        Files.writeString(canonical, """
                prependRequirePatch = true

                [languagePipelines.".JS"]
                frontend = "example:native"
                sourceChecks = ["example:second", "example:first"]
                outputChecks = []
                probe = { language = "typescript", backend = "builtin" }

                [languagePipelines.mjs]
                sourceChecks = ["example:first"]
                outputChecks = ["example:first"]

                [languagePipelines.".cjs"]
                sourceChecks = []
                """);
        var loader = new SandboxConfigLoader();
        var expected = Map.of(
                ".js", new SandboxConfig.LanguagePipeline("example:native",
                        List.of("example:second", "example:first"), List.of(),
                        new SandboxConfig.ProbeReference("typescript", "builtin")),
                ".mjs", new SandboxConfig.LanguagePipeline(null, List.of("example:first"), List.of("example:first")),
                ".cjs", new SandboxConfig.LanguagePipeline(null, List.of(), null));

        var loaded = loader.load(canonical).languagePipelines();
        assertEquals(expected, loaded);
        assertThrows(UnsupportedOperationException.class, loaded::clear);
        assertThrows(UnsupportedOperationException.class, () -> loaded.get(".js").sourceChecks().clear());
        assertFalse(Files.readString(canonical).contains("prependRequirePatch"));
        assertEquals(expected, loader.load(canonical, false).languagePipelines());
    }

    @Test
    void corruptTomlFailsWithoutChangingAnyBytes() throws Exception {
        Path engineConfig = writeEngineConfig("prependRequirePatch = true\nthis is not valid toml [[[\n");
        byte[] before = Files.readAllBytes(engineConfig);
        EngineConfigException failure = assertInvalidConfig(engineConfig,
                () -> new SandboxConfigLoader().load(engineConfig));
        assertNotNull(failure.getCause());
        assertArrayEquals(before, Files.readAllBytes(engineConfig));
    }

    @Test
    void rejectsMalformedPipelineSettingsBeforeWritingDefaultsOrRemovingRetiredKeys() throws Exception {
        var cases = Map.ofEntries(
                Map.entry("languagePipelines = false\n", "languagePipelines"),
                Map.entry("[languagePipelines]\njs = []\n", "languagePipelines[\"js\"]"),
                Map.entry("[languagePipelines.\".\"]\nsourceChecks = []\n", "Invalid script extension"),
                Map.entry("[languagePipelines.\"path/js\"]\nsourceChecks = []\n", "Invalid script extension"),
                Map.entry("[languagePipelines.\".JS\"]\nsourceChecks = []\n[languagePipelines.js]\nsourceChecks = []\n",
                        "Duplicate script extension .js"),
                Map.entry("[languagePipelines.js]\nfrontend = false\n", ".frontend"),
                Map.entry("[languagePipelines.js]\nsourceChecks = \"example:check\"\n", ".sourceChecks"),
                Map.entry("[languagePipelines.js]\noutputChecks = [1]\n", ".outputChecks[0]"),
                Map.entry("[languagePipelines.js]\nsourceChecks = [\"test:first\", \"test:second\", \"test:first\"]\n",
                        "Duplicate provider ID in languagePipelines[\"js\"].sourceChecks: test:first"),
                Map.entry("[languagePipelines.js]\nprobe = \"example:probe\"\n", ".probe"),
                Map.entry("[languagePipelines.js]\nprobe = [{ language = \"typescript\", backend = \"builtin\" }]\n",
                        ".probe"),
                Map.entry("[languagePipelines.js]\nprobe = { backend = \"builtin\" }\n", ".probe.language"),
                Map.entry("[languagePipelines.js]\nprobe = { language = \"typescript\" }\n", ".probe.backend"),
                Map.entry("[languagePipelines.js]\nprobe = { language = \"  \", backend = \"builtin\" }\n",
                        ".probe.language"),
                Map.entry("[languagePipelines.js]\nprobe = { language = \"typescript\", backend = \"\" }\n",
                        ".probe.backend"),
                Map.entry("[languagePipelines.js]\nprobe = { language = 42, backend = \"builtin\" }\n",
                        ".probe.language"),
                Map.entry("[languagePipelines.js]\nprobe = { language = \"typescript\", backend = { name = \"builtin\" } }\n",
                        ".probe.backend"),
                Map.entry("[languagePipelines.js]\nprobe = { language = \"typescript\", backend = \"builtin\", extra = true }\n",
                        ".probe.extra"),
                Map.entry("[languagePipelines.js]\nextra = false\n", ".extra"),
                Map.entry("[languageFrontends]\njs = \"example:native\"\n", "languageFrontends"));
        for (var invalid : cases.entrySet()) {
            Path config = writeEngineConfig("prependRequirePatch = true\n" + invalid.getKey());
            byte[] before = Files.readAllBytes(config);
            var failure = assertInvalidConfig(config, () -> new SandboxConfigLoader().load(config));
            assertTrue(failure.getMessage().contains(invalid.getValue()), failure.getMessage());
            assertArrayEquals(before, Files.readAllBytes(config), invalid.getKey());
        }
    }

    @Test
    void legacyIsReadOnlyAndUsedOnlyWhenCanonicalIsAbsent() throws Exception {
        var paths = NekoJSPaths.fromGameDir(tempDir);
        Path canonical = paths.engineConfig();
        Path legacy = paths.legacyEngineConfig();
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, """
                prependRequirePatch = true
                allowThreads = true
                [languagePipelines.".js"]
                sourceChecks = []
                """);
        byte[] legacyBytes = Files.readAllBytes(legacy);
        SandboxConfig saved = ClassFilter.INSTANCE.config();
        try {
            var settings = ClassFilter.loadEngineSettings(paths);
            assertEquals(legacy, settings.path());
            assertTrue(settings.config().allowThreads());
            assertEquals(List.of(), settings.config().languagePipelines().get(".js").sourceChecks());
            assertSame(settings.config(), ClassFilter.INSTANCE.config());
            assertFalse(Files.exists(canonical.getParent()));
            assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));

            Files.createDirectories(canonical);
            assertInvalidConfig(canonical, () -> ClassFilter.loadEngineSettings(paths));
            assertTrue(Files.isDirectory(canonical));
            Files.delete(canonical);

            byte[] corrupt = {(byte) 0xc3, 0x28};
            Files.write(canonical, corrupt);
            assertInvalidConfig(canonical, () -> ClassFilter.loadEngineSettings(paths));
            assertArrayEquals(corrupt, Files.readAllBytes(canonical));
            assertSame(settings.config(), ClassFilter.INSTANCE.config());

            Files.writeString(canonical, "allowThreads = false\n");
            var canonicalSettings = ClassFilter.loadEngineSettings(paths);
            assertEquals(canonical, canonicalSettings.path());
            assertFalse(canonicalSettings.config().allowThreads());
            assertTrue(canonicalSettings.config().languagePipelines().isEmpty());
            assertSame(canonicalSettings.config(), ClassFilter.INSTANCE.config());
            assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        } finally {
            ClassFilter.INSTANCE.updateConfig(saved);
        }
    }

    @Test
    void danglingCanonicalSymbolicLinkDoesNotFallBackToLegacy() throws Exception {
        var paths = NekoJSPaths.fromGameDir(tempDir);
        Path canonical = paths.engineConfig();
        Path legacy = paths.legacyEngineConfig();
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "allowThreads = true\n");
        Files.createDirectories(canonical.getParent());
        Path absentTarget = canonical.resolveSibling("absent.toml");
        try {
            Files.createSymbolicLink(canonical, absentTarget);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "Symbolic link creation is unavailable: " + e);
        }
        SandboxConfig saved = ClassFilter.INSTANCE.config();
        try {
            assertInvalidConfig(canonical, () -> ClassFilter.loadEngineSettings(paths));
            assertTrue(Files.isSymbolicLink(canonical));
            assertFalse(Files.exists(absentTarget));
            assertSame(saved, ClassFilter.INSTANCE.config());
        } finally {
            ClassFilter.INSTANCE.updateConfig(saved);
        }
    }

    private static EngineConfigException assertInvalidConfig(Path path, Runnable load) {
        var failure = assertThrows(EngineConfigException.class, load::run);
        assertTrue(failure.getMessage().contains("[NEKO-1030]"), failure.getMessage());
        assertTrue(failure.getMessage().contains(path.toString()), failure.getMessage());
        return failure;
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
        for (String key : List.of("scriptEvaluationTimeoutSeconds", "scriptStatementLimit", "scriptRunawayTimeoutSeconds")) {
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
    void retainedSandboxConstructorsUseDefaultPipelineSelections() {
        var shortConfig = new SandboxConfig(false, false, false, false, true, true, false, true);
        var originalConfig = new SandboxConfig(false, false, false, false, true, true, false, true,
                SandboxConfig.PACK_SYNC_ALL, true, true);
        var fullConfig = new SandboxConfig(false, false, false, false, true, true, false, true,
                SandboxConfig.PACK_SYNC_ALL, true, true, true);
        assertTrue(shortConfig.languagePipelines().isEmpty());
        assertTrue(originalConfig.languagePipelines().isEmpty());
        assertTrue(fullConfig.languagePipelines().isEmpty());
        assertTrue(fullConfig.mixinJsEnabled());
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
        for (String source : List.of("[mixinjs]\nenabled = false\n", "allowThreads = true\n",
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
        var cases = Map.of("true", true, "false", false, "\"true\"", false, "1", false, "[]", false);
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
