package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.core.config.EngineConfigException;
import com.tkisor.nekojs.core.config.SandboxConfigLoader;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.LanguagePipelineSelection;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.preflight.ScriptPreflightCatalog;
import com.tkisor.nekojs.core.preflight.BuiltinJavaScriptPreflight;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProbePipelineSelectionTest {
    @TempDir Path game;

    @BeforeAll static void initPlatform() { TestPlatformInit.ensureInitialized(); }

    @Test void combinesFrozenJavaScriptWithTypeScriptAuthoringAndRetainsLegacyDispatch() throws Exception {
        var builtin = new TypeScriptProbeBackend();
        var equal = new Backend("typescript", "equal", 0, Set.of());
        var override = new Backend("typescript", "override", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING));
        var legacy = new Backend("typescript", "legacy", 10, Set.of());
        var python = new PythonProbeBackend();
        var registry = registry(builtin, equal, override, legacy, python);
        var engines = selection(registry, references(".js", "typescript", "override"));
        var cfg = config("legacy", "ts", "py");
        assertEquals(List.of(override), engines.select(null, null, cfg));
        assertEquals(List.of(override), engines.select("typescript", null, cfg));
        assertEquals(List.of(legacy), engines.select("typescript", "legacy", cfg));
        assertEquals(List.of(), engines.select("typescript", "missing", cfg));
        assertEquals(List.of(python, legacy, override, builtin, equal), engines.selectAll());

        var unchanged = selection(registry, "");
        assertEquals(List.of(builtin), unchanged.select(null, null, cfg));
        assertEquals(List.of(builtin), unchanged.select("typescript", null, config("builtin", "ts", "py")));
        assertEquals(List.of(legacy), unchanged.select("typescript", null, config("unknown", "ts", "py")));
        assertEquals(List.of(legacy), unchanged.select("typescript", null, config(null, "ts", "py")));
    }

    @Test void rejectsUnknownMismatchedAndUndeclaredAuthoringCapabilities() throws Exception {
        var registry = registry(new TypeScriptProbeBackend(), new PythonProbeBackend(),
                new Backend("typescript", "old", 10, Set.of()),
                new Backend("typescript", "next", 10, Set.of("nekojs:typescript-authoring/v2")));
        for (var reference : List.of(new String[]{"typescript", "missing"}, new String[]{"python", "builtin"},
                new String[]{"typescript", "old"}, new String[]{"typescript", "next"})) {
            var failure = assertThrows(EngineConfigException.class,
                    () -> selection(registry, references(".js", reference[0], reference[1])));
            assertTrue(failure.getMessage().contains("NEKO-1030"));
            assertTrue(failure.getMessage().contains(".js"));
            assertTrue(failure.getMessage().contains(reference[0] + ":" + reference[1]));
        }
    }

    @Test void deduplicatesIdenticalTargetsButRejectsDifferentBackendsForTheSameTarget() throws Exception {
        var builtin = new TypeScriptProbeBackend();
        var registry = registry(builtin,
                new Backend("typescript", "alternate", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING)));
        var first = references(".js", "typescript", "builtin");
        assertEquals(List.of(builtin), selection(registry, first + references(".mjs", "typescript", "builtin"))
                .select(null, null, ProbeConfig.defaultConfig()));
        var failure = assertThrows(EngineConfigException.class,
                () -> selection(registry, first + references(".mjs", "typescript", "alternate")));
        assertTrue(failure.getMessage().contains(".js selects typescript:builtin"));
        assertTrue(failure.getMessage().contains(".mjs selects typescript:alternate"));
    }

    @Test void rejectsOverlappingPathsBeforeGenerationOrExternalArtifacts() throws Exception {
        var registry = registry(new Backend("typescript", "v1", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING)),
                new Backend("second", "v1", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING)));
        var selector = selection(registry, references(".js", "typescript", "v1") + references(".mjs", "second", "v1"));
        var paths = NekoJSPaths.fromGameDir(game);
        selector.validateConfiguredOutputs(game.resolve("engine.toml"), paths, config(null, "ts", "py"));
        var config = config(null, "ts", "ts/child/..");
        var failure = assertThrows(EngineConfigException.class,
                () -> selector.validateConfiguredOutputs(game.resolve("engine.toml"), paths, config));
        assertTrue(failure.getMessage().contains("overlaps"));
        var coordinator = new ProbeCoordinator(paths, ignored -> fail("External artifacts must not be generated"));
        var request = coordinator.selectRequest(selector, null, null, config);
        assertTrue(request.problem().contains("NEKO-1032"));
        assertFalse(coordinator.runProbe(emptySnapshot(), request).getFirst().success());
        assertFalse(Files.exists(game.resolve(".scratch-probe")));
    }

    @Test void legacyCleanupCannotRemoveAnotherExplicitBackendsOutput() throws Exception {
        var registry = registry(new Backend("typescript", "v1", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING)),
                new Backend("second", "v1", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING)));
        var selector = selection(registry, references(".js", "typescript", "v1") + references(".mjs", "second", "v1"));
        var paths = NekoJSPaths.fromGameDir(game);
        var coordinator = new ProbeCoordinator(paths, ignored -> fail("External artifacts must not be generated"));
        for (var pair : List.of(List.of("ts", "ts.old"), List.of("ts.staging/child", "ts"))) {
            Path existing = game.resolve(".scratch-probe").resolve(pair.get(1)).resolve("existing.txt");
            Files.createDirectories(existing.getParent());
            Files.writeString(existing, "keep");
            var config = config(null, pair.get(0), pair.get(1));
            var failure = assertThrows(EngineConfigException.class,
                    () -> selector.validateConfiguredOutputs(game.resolve("engine.toml"), paths, config));
            assertTrue(failure.getMessage().contains("overlaps"));
            var request = coordinator.selectRequest(selector, null, null, config);
            assertTrue(request.problem().contains("NEKO-1032"));
            assertFalse(coordinator.runProbe(emptySnapshot(), request).getFirst().success());
            assertEquals("keep", Files.readString(existing));
        }
    }

    @Test void generationAndEditorUseTheSameCapturedOutputAfterOldSettingsChange() throws Exception {
        var backend = new Backend("typescript", "v1", 1, Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING));
        var selector = selection(registry(backend), references(".js", "typescript", "v1"));
        var paths = NekoJSPaths.fromGameDir(game);
        var coordinator = new ProbeCoordinator(paths, ignored -> backend.trace.add("base-editor"));
        var request = coordinator.selectRequest(selector, null, null, config(null, "captured", "py"));
        Files.createDirectories(paths.probeConfig().getParent());
        Files.writeString(paths.probeConfig(), """
                enabled = false
                [languages.typescript]
                outputDir = "changed"
                """);
        assertFalse(coordinator.readConfig().enabled());
        var result = coordinator.runProbe(emptySnapshot(), request).getFirst();
        var out = game.toRealPath().resolve(".scratch-probe/captured");
        assertTrue(result.success(), result.message());
        assertEquals(out, result.outputDir());
        assertEquals(List.of("base-editor", "render:" + out, "editor:" + out), backend.trace);
        assertEquals("output", Files.readString(out.resolve("marker.txt")));
    }

    private ProbeBackendSelector selection(ProbeBackendRegistry registry, String text) throws Exception {
        var file = game.resolve("engine.toml");
        Files.writeString(file, text);
        var config = new SandboxConfigLoader().load(file, false);
        var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.freeze();
        var checks = new ScriptPreflightCatalog.Collector();
        checks.register(BuiltinJavaScriptPreflight.INSTANCE);
        var pipeline = LanguagePipelineSelection.resolve(file, config, compilers, checks.freeze());
        return ProbeBackendSelector.resolve(file, config, pipeline.configuredFrontends(), registry);
    }

    private static String references(String ext, String language, String backend) {
        return """
                [languagePipelines."%s"]
                probe = { language = "%s", backend = "%s" }
                """.formatted(ext, language, backend);
    }

    private static ProbeBackendRegistry registry(ProbeBackend... backends) {
        var registry = new ProbeBackendRegistry();
        for (var backend : backends) registry.register(backend, "fixture");
        registry.lock();
        return registry;
    }

    private static ProbeConfig config(String preferred, String ts, String other) {
        return new ProbeConfig(true, ".scratch-probe", ProbeConfig.ScanConfig.defaultScan(), Map.of(
                "typescript", new ProbeConfig.LanguageConfig(preferred, ts),
                "second", new ProbeConfig.LanguageConfig(null, other)));
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static final class Backend implements ProbeBackend {
        private final String languageId;
        private final String name;
        private final int priority;
        private final Set<String> authoringCapabilities;
        private final List<String> trace = new ArrayList<>();

        Backend(String languageId, String name, int priority, Set<String> authoringCapabilities) {
            this.languageId = languageId;
            this.name = name;
            this.priority = priority;
            this.authoringCapabilities = authoringCapabilities;
        }

        @Override public String languageId() { return languageId; }
        @Override public String name() { return name; }
        @Override public int priority() { return priority; }
        @Override public Set<String> authoringCapabilities() { return authoringCapabilities; }

        @Override public Map<String, String> render(ProbeContext ctx) {
            trace.add("render:" + ctx.languageDir());
            return Map.of("marker.txt", "output");
        }

        @Override public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
            trace.add("editor:" + ctx.languageDir());
        }
    }
}
