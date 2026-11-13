package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.authoring.AuthoringPaths;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ProbeAuthoringPathsTest {
    @BeforeAll static void platform() { TestPlatformInit.ensureInitialized(); }
    @TempDir Path directory;

    @Test void legacyContextConstructorsUseGameRelativeDefaultRegardlessOfProbeBaseDir() throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        ProbeConfig config = config("nested/probe/output");
        var snapshot = emptySnapshot();
        Path output = directory.resolve(config.baseDir()).resolve("typescript");
        List<ProbeContext> contexts = List.of(
                new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output),
                new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null),
                new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null, ProbeOverrides.empty()),
                new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null, ProbeOverrides.empty(), null));
        for (var context : contexts) {
            assertEquals(directory.toRealPath().resolve(".github/neko_ai"), context.authoringPaths().root());
            assertEquals(context.authoringPaths().root(), context.authoringPaths().root());
            assertEquals(output, context.languageDir());
        }
        assertFalse(Files.exists(directory.resolve("nested")));
        assertFalse(Files.exists(directory.resolve(".github")));
    }

    @Test void legacyRenderOnlyContextsDoNotRequireOrCreateGameDirectory() {
        Path missing = directory.resolve("not-created");
        NekoJSPaths paths = NekoJSPaths.fromGameDir(missing);
        var config = config("nested/probe/output");
        var snapshot = emptySnapshot();
        Path output = missing.resolve("typescript");
        assertNotNull(new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output));
        assertNotNull(new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null));
        assertNotNull(new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null, null));
        assertNotNull(new ProbeContext.Of(snapshot, List.of(), config, paths, "typescript", output, null, null, null));
        assertFalse(Files.exists(missing));
    }

    @Test void runPinsOneRootAcrossRebindAndConfigChangeAndOldCloseCannotUnbindReplacement() throws Exception {
        NekoJSPaths paths = configuredPaths("nested/probe/output");
        AuthoringPaths first = AuthoringPaths.resolve(directory, "custom/first-ai");
        AuthoringPaths second = AuthoringPaths.resolve(directory, "custom/second-ai");
        AutoCloseable old = ProbeCoordinator.bindAuthoringPaths(first);
        AtomicReference<AutoCloseable> replacement = new AtomicReference<>();
        List<AuthoringPaths> artifactRoots = new ArrayList<>();
        List<ProbeContext> seen = new ArrayList<>();
        ProbeExternalArtifacts artifacts = new ProbeExternalArtifacts() {
            @Override public void generate(Path output) { fail("The captured-path overload must be used."); }
            @Override public void generate(Path output, AuthoringPaths selected) throws Exception {
                assertEquals(paths.gameDir().resolve("nested/probe/output"), output);
                artifactRoots.add(selected);
                if (replacement.get() == null) {
                    Files.writeString(paths.config().resolve("ai.toml"), "[output]\ndirectory = \"custom/second-ai\"\n");
                    replacement.set(ProbeCoordinator.bindAuthoringPaths(second));
                    old.close();
                }
            }
        };
        var coordinator = new ProbeCoordinator(paths, artifacts);
        try {
            var results = coordinator.runProbe(emptySnapshot(), List.of(capture("first", seen::add), capture("second", seen::add)));
            assertTrue(results.stream().allMatch(ProbeBackend.GenerateResult::success));
            assertEquals(2, seen.size());
            assertSame(first, artifactRoots.get(0));
            seen.forEach(context -> assertSame(first, context.authoringPaths()));
            seen.clear();
            assertTrue(coordinator.runProbe(emptySnapshot(), List.of(capture("first", seen::add))).get(0).success());
            assertSame(second, artifactRoots.get(1));
            assertSame(second, seen.get(0).authoringPaths());
            replacement.get().close();
            seen.clear();
            assertTrue(coordinator.runProbe(emptySnapshot(), List.of(capture("first", seen::add))).get(0).success());
            assertEquals(directory.toRealPath().resolve(".github/neko_ai"), seen.get(0).authoringPaths().root());
            assertNotSame(first, seen.get(0).authoringPaths());
        } finally {
            old.close();
            if (replacement.get() != null) replacement.get().close();
        }
    }

    @Test void boundRootFromAnotherGameDoesNotLeakIntoStandaloneCoordinator() throws Exception {
        NekoJSPaths paths = configuredPaths("nested/probe/output");
        Path otherGame = Files.createDirectory(directory.resolve("other-game"));
        AuthoringPaths other = AuthoringPaths.resolve(otherGame, "custom-ai");
        List<ProbeContext> seen = new ArrayList<>();
        try (var ignored = ProbeCoordinator.bindAuthoringPaths(other)) {
            assertTrue(new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE)
                    .runProbe(emptySnapshot(), List.of(capture("typescript", seen::add))).get(0).success());
        }
        assertEquals(directory.toRealPath().resolve(".github/neko_ai"), seen.get(0).authoringPaths().root());
        assertFalse(Files.exists(otherGame.resolve("custom-ai")));
    }

    @Test void legacyExternalArtifactLambdaStillReceivesOnlyDeclarationBaseDir() throws Exception {
        NekoJSPaths paths = configuredPaths("nested/probe/output");
        AtomicReference<Path> output = new AtomicReference<>();
        ProbeExternalArtifacts oldCallback = output::set;
        assertTrue(new ProbeCoordinator(paths, oldCallback)
                .runProbe(emptySnapshot(), List.of(capture("typescript", context -> {}))).get(0).success());
        assertEquals(paths.gameDir().resolve("nested/probe/output"), output.get());
    }

    @Test void defaultArtifactsPutRolesUnderSelectedRootWithCorrectPointerNotProbeParent() throws Exception {
        Path probeOutput = directory.resolve("nested/probe/output");
        AuthoringPaths selected = AuthoringPaths.resolve(directory, "custom/ai-output");
        ProbeExternalArtifacts.DEFAULT.generate(probeOutput, selected);
        for (String role : List.of("nekojs-explore.agent.md", "nekojs-planner.agent.md", "nekojs-survey.agent.md")) {
            String text = Files.readString(selected.agents().resolve(role));
            assertTrue(text.contains("<../current.json>"));
            assertFalse(text.contains("nekojs/ai/current.json"));
        }
        assertFalse(Files.exists(directory.resolve("nested/probe/.github")));
        assertFalse(Files.exists(directory.resolve(".github/agents")));
        assertFalse(Files.exists(probeOutput));
    }

    private NekoJSPaths configuredPaths(String baseDir) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        Files.createDirectories(paths.probeConfig().getParent());
        Files.writeString(paths.probeConfig(), "enabled = true\nbaseDir = \"" + baseDir + "\"\n[scan]\nmode = \"SMART\"\nforceScanMods = []\n");
        return paths;
    }

    private static ProbeConfig config(String baseDir) {
        return new ProbeConfig(true, baseDir, ProbeConfig.ScanConfig.defaultScan());
    }

    private static ProbeBackend capture(String language, Consumer<ProbeContext> consumer) {
        return new ProbeBackend() {
            @Override public String languageId() { return language; }
            @Override public String name() { return "paths-test"; }
            @Override public Map<String, String> render(ProbeContext context) { return Map.of(); }
            @Override public GenerateResult generate(ProbeContext context) {
                consumer.accept(context);
                return GenerateResult.success(0, 0, context.languageDir());
            }
        };
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
