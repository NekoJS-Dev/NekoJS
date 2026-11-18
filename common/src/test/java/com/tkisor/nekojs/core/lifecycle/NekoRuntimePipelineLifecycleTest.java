package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.preflight.ScriptPreflight;
import com.tkisor.nekojs.api.preflight.ScriptPreflightRegistry;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.EngineConfigException;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin;
import com.tkisor.nekojs.core.preflight.ScriptPreflightRunner.ProviderFailure;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoRuntimePipelineLifecycleTest {
    private static final String EXTENSION = ".lifecycle";
    private static final String ORIGINAL_FRONTEND = "fixture:original";
    private static final String UPDATED_FRONTEND = "fixture:updated";
    private static final String GUARD = "fixture:guard";
    private static final String FIRST = "fixture:first";
    private static final String REPLACEMENT = "fixture:replacement";

    @TempDir Path directory;

    @Test
    void malformedAndInvalidPipelineSettingsStopAssemblyBeforeStartup() throws Exception {
        runIsolated("malformed", Files.createDirectory(directory.resolve("malformed")));
        runIsolated("invalid", Files.createDirectory(directory.resolve("invalid")));
    }

    @Test
    void missingProbeBackendStopsAssemblyBeforeStartup() throws Exception {
        runIsolated("invalid-probe", Files.createDirectory(directory.resolve("invalid-probe")));
    }

    @Test
    void failedStartupPackClosesTheOpenedPreflightWorker() throws Exception {
        runIsolated("failed-startup", Files.createDirectory(directory.resolve("failed-startup")));
    }

    @Test
    void reloadKeepsTheFrozenPipelineAndFailedCandidateUntilRuntimeRestart() throws Exception {
        Path gameDir = Files.createDirectory(directory.resolve("game"));
        runIsolated("reload", gameDir);
        runIsolated("restart", gameDir);
    }

    // Assembly publishes process-wide state. A fresh JVM also models a loader restart without resetting globals.
    private void runIsolated(String scenario, Path gameDir) throws Exception {
        Path log = directory.resolve(scenario + ".log");
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path java = bin.resolve(Files.exists(bin.resolve("java.exe")) ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "-classpath", testClasspath(),
                RuntimeProcess.class.getName(), scenario, gameDir.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(90, TimeUnit.SECONDS), () -> scenario + " runtime did not exit");
            assertEquals(0, child.exitValue(), Files.readString(log));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static String testClasspath() throws Exception {
        var entries = new LinkedHashSet<String>();
        // Gradle's worker places the test runtime classpath on a child loader, not java.class.path.
        for (ClassLoader loader = RuntimeProcess.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) {
                    if ("file".equals(url.getProtocol())) entries.add(Path.of(url.toURI()).toString());
                }
            }
        }
        entries.addAll(List.of(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))));
        return String.join(File.pathSeparator, entries);
    }

    public static final class RuntimeProcess {
        public static void main(String[] args) throws Exception {
            Path gameDir = Path.of(args[1]);
            TestPlatformInit.ensureInitialized(gameDir);
            NekoJSPaths paths = NekoJSPaths.get();
            paths.initFolders();
            var plugin = new PipelineFixture();
            switch (args[0]) {
                case "malformed", "invalid" -> assertStartupRejected(paths, plugin, args[0]);
                case "failed-startup" -> assertFailedStartupClosesWorker(paths, plugin);
                case "invalid-probe" -> assertMissingProbeStopsStartup(paths, plugin);
                case "reload" -> assertFrozenReloadAndRollback(paths, plugin);
                case "restart" -> assertRestartPicksUpdatedSettings(plugin);
                default -> throw new AssertionError("Unknown scenario: " + args[0]);
            }
        }

        private static void assertStartupRejected(NekoJSPaths paths, PipelineFixture plugin, String scenario) throws Exception {
            Files.writeString(paths.startupScripts().resolve("first.js"), "recordPipeline('first startup script');");
            Files.writeString(paths.engineConfig(), "malformed".equals(scenario)
                    ? "[languagePipelines.\".lifecycle\"\n"
                    : settings("fixture:missing", "[]"));

            var failure = assertThrows(EngineConfigException.class, () -> {
                try (var ignored = assemble(plugin)) {
                    // A successful assembly would already have evaluated the STARTUP entry.
                }
            });
            assertTrue(failure.getMessage().contains(paths.engineConfig().toString()), failure::getMessage);
            if ("invalid".equals(scenario)) {
                assertTrue(failure.getMessage().contains(EXTENSION), failure::getMessage);
                assertTrue(failure.getMessage().contains("fixture:missing"), failure::getMessage);
                assertTrue(failure.getMessage().contains(ORIGINAL_FRONTEND), failure::getMessage);
            }
            assertEquals(List.of(), plugin.executed);
            assertEquals(0, plugin.initCalls);
        }

        private static void assertMissingProbeStopsStartup(NekoJSPaths paths, PipelineFixture plugin) throws Exception {
            Files.writeString(paths.startupScripts().resolve("first.js"), "recordPipeline('first startup script');");
            Files.writeString(paths.engineConfig(), """
                    [languagePipelines.".js"]
                    probe = { language = "typescript", backend = "not-installed" }
                    """);

            var failure = assertThrows(EngineConfigException.class, () -> {
                try (var ignored = assemble(plugin)) {
                    // A successful assembly would already have evaluated the STARTUP entry.
                }
            });
            assertTrue(failure.getMessage().contains("NEKO-1030"), failure::getMessage);
            assertTrue(failure.getMessage().contains("typescript:not-installed"), failure::getMessage);
            assertTrue(failure.getMessage().contains(".js"), failure::getMessage);
            assertEquals(List.of(), plugin.executed);
            assertEquals(0, plugin.initCalls);
            assertFalse(Files.exists(paths.probeConfig()));
        }

        private static void assertFailedStartupClosesWorker(NekoJSPaths paths, PipelineFixture plugin) throws Exception {
            Files.writeString(paths.engineConfig(), """
                    scriptMemberValidation = true
                    [languagePipelines.".js"]
                    frontend = "nekojs:javascript"
                    sourceChecks = ["fixture:guard"]
                    outputChecks = []
                    """);
            Path pack = paths.packsRoot().resolve("bootpack");
            Files.createDirectories(pack.resolve("startup_scripts"));
            Files.writeString(pack.resolve("manifest.json"), """
                    {"formatVersion":1,"id":"bootpack","version":"1.0.0","entries":{"startup":["main.js"]}}
                    """);
            Files.writeString(pack.resolve("startup_scripts/main.js"), "reject");

            var failure = assertThrows(IllegalStateException.class, () -> {
                try (var ignored = assemble(plugin)) {
                    // A failed STARTUP pack must not publish a usable root.
                }
            });
            assertTrue(failure.getMessage().contains("[NEKO-1017] Package loading failed"), failure::getMessage);
            assertEquals(List.of(GUARD + "/nekojs:javascript"), plugin.fullChecks);
            assertEquals(List.of(GUARD), plugin.openedWorkers);
            assertEquals(List.of(GUARD), plugin.closedWorkers);
        }

        private static void assertFrozenReloadAndRollback(NekoJSPaths paths, PipelineFixture plugin) throws Exception {
            Files.writeString(paths.engineConfig(), settings(ORIGINAL_FRONTEND, "[\"fixture:first\", \"fixture:guard\"]"));
            Files.writeString(paths.startupScripts().resolve("first" + EXTENSION), "startup");
            Path entry = paths.serverScripts().resolve("entry" + EXTENSION);
            Files.writeString(entry, "initial");
            try (var root = assemble(plugin)) {
                assertEquals(List.of("original:startup"), plugin.executed);
                root.load(ScriptType.SERVER);
                var manager = root.scriptManagerOf(ScriptType.SERVER);
                long initial = manager.generationId();
                assertEquals(List.of("original:startup", "original:initial"), plugin.executed);

                Files.writeString(paths.engineConfig(), settings(UPDATED_FRONTEND, "[\"fixture:replacement\"]"));
                Files.writeString(entry, "reloaded");
                plugin.fullChecks.clear();
                var result = root.reload(ScriptType.SERVER);
                assertTrue(result.success());
                assertEquals(ReloadPhase.COMMIT, result.phase());
                assertTrue(result.generation() > initial);
                assertEquals(List.of(FIRST + "/" + ORIGINAL_FRONTEND, GUARD + "/" + ORIGINAL_FRONTEND), plugin.fullChecks);
                assertEquals(List.of("original:startup", "original:initial", "original:reloaded"), plugin.executed);
                assertEquals(1, plugin.initCalls);
                var active = manager.activeContextForPackOwner();

                Files.writeString(entry, "reject");
                var failure = assertThrows(NekoReloadException.class, () -> root.reload(ScriptType.SERVER)).report();
                assertEquals(ReloadPhase.EXECUTION, failure.phase());
                assertTrue(failure.sourceLocation().endsWith(entry.getFileName().toString()));
                Throwable cause = failure.error();
                while (cause != null && !(cause instanceof ProviderFailure)) cause = cause.getCause();
                var provider = assertInstanceOf(ProviderFailure.class, cause);
                assertEquals(GUARD, provider.providerId());
                assertEquals(ScriptPreflight.Stage.SOURCE, provider.stage());
                assertEquals(ScriptPreflight.Scope.FULL, provider.scope());
                assertEquals(result.generation(), manager.generationId());
                assertSame(active, manager.activeContextForPackOwner());
                assertFalse(root.isActiveFailed(ScriptType.SERVER));
                assertEquals("original:reloaded", active.eval("js", "globalThis.pipelineMarker").asString());
                assertEquals(List.of("original:startup", "original:initial", "original:reloaded"), plugin.executed);
            }
        }

        private static void assertRestartPicksUpdatedSettings(PipelineFixture plugin) throws Exception {
            try (var root = assemble(plugin)) {
                // The previous process left both the updated TOML and the source rejected by its old guard.
                root.load(ScriptType.SERVER);
                assertEquals(List.of("updated:startup", "updated:reject"), plugin.executed);
                assertEquals(List.of(REPLACEMENT + "/" + UPDATED_FRONTEND, REPLACEMENT + "/" + UPDATED_FRONTEND),
                        plugin.fullChecks);
                assertEquals(1, plugin.initCalls);
            }
        }

        private static NekoRuntimeRoot assemble(PipelineFixture plugin) throws Exception {
            return NekoRuntimeAssembly.assemble(ScriptEventBridge.EMPTY, new ScriptPropertyRegistry.Impl(),
                    List.of(owned("nekojs", new NekoCommonBuiltinPlugin()), owned("fixture", plugin)), ignored -> {}).root();
        }

        private static OwnedPlugin owned(String owner, NekoJSPlugin plugin) throws Exception {
            Class<?> type = plugin.getClass();
            return new OwnedPlugin(new PluginIdentity(owner, type.getName(),
                    type.getProtectionDomain().getCodeSource().getLocation().toURI()), plugin);
        }

        private static String settings(String frontend, String checks) {
            return """
                    scriptMemberValidation = true
                    [languagePipelines.".lifecycle"]
                    frontend = "%s"
                    sourceChecks = %s
                    outputChecks = []
                    """.formatted(frontend, checks);
        }
    }

    private static final class PipelineFixture implements NekoJSPlugin {
        private final List<String> executed = new ArrayList<>();
        private final List<String> fullChecks = new ArrayList<>();
        private final List<String> openedWorkers = new ArrayList<>();
        private final List<String> closedWorkers = new ArrayList<>();
        private int initCalls;

        @Override
        public void init() {
            initCalls++;
        }

        @Override
        public void registerBinding(BindingRegistry registry) {
            registry.register("recordPipeline", (ProxyExecutable) args -> {
                executed.add(args[0].asString());
                return null;
            });
        }

        @Override
        public void registerScriptCompilers(ScriptCompilerRegistry registry) {
            registerFrontend(registry, ORIGINAL_FRONTEND, "original");
            registerFrontend(registry, UPDATED_FRONTEND, "updated");
        }

        private static void registerFrontend(ScriptCompilerRegistry registry, String id, String marker) {
            IScriptCompiler compiler = new IScriptCompiler() {
                @Override public boolean canCompile(String extension) { return EXTENSION.equals(extension); }
                @Override public String compile(Path file, String source) {
                    return "globalThis.pipelineMarker = '" + marker + ":" + source.strip() + "';"
                            + " recordPipeline(globalThis.pipelineMarker);";
                }
            };
            registry.registerFrontend(id, "1", new NekoScriptLanguage("lifecycle-fixture", Set.of(EXTENSION), compiler),
                    new FrontendSemantics(FrontendSemantics.JAVASCRIPT_SOURCE, FrontendSemantics.JAVASCRIPT_OUTPUT, Set.of()));
        }

        @Override
        public void registerScriptPreflights(ScriptPreflightRegistry registry) {
            for (String id : List.of(FIRST, GUARD, REPLACEMENT)) {
                registry.register(new ScriptPreflight() {
                    @Override
                    public Descriptor descriptor() {
                        return new Descriptor(id, "1", Map.of(Stage.SOURCE, Set.of(FrontendSemantics.JAVASCRIPT_SOURCE)),
                                Set.of(Scope.FULL, Scope.BINDINGS_ONLY), Set.of());
                    }

                    @Override
                    public Worker open() {
                        openedWorkers.add(id);
                        return new Worker() {
                            @Override
                            public List<Diagnostic> check(Request request) throws IOException {
                                if (request.scope() == Scope.FULL) fullChecks.add(id + "/" + request.frontendId());
                                if (GUARD.equals(id) && "reject".equals(request.text().strip())) {
                                    throw new IOException("Injected pipeline provider failure");
                                }
                                return List.of();
                            }

                            @Override
                            public void close() {
                                closedWorkers.add(id);
                            }
                        };
                    }
                });
            }
        }
    }
}
