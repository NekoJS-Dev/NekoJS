package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.testing.TestRunReport;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class TestRunReportTest {
    @TempDir Path gameDirectory;

    @BeforeAll static void initPlatform() throws Exception {
        TestPlatformInit.ensureInitialized();
        // Process-owned log appenders must not hold handles inside a per-test temporary directory.
        ScriptTypeEnv.class.getMethod("logger", ScriptType.class).invoke(null, ScriptType.TEST);
    }

    @Test
    void caughtAssertionFailureDoesNotTurnCompletedExecutionIntoAssertionsPassed() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("caught.mjs", "try { Test.assertTrue(false, 'expected failure'); } catch (error) {}\n");
            fixture.root.runTests();
            Object report = fixture.report();
            assertNotNull(report, "a completed TEST attempt must publish a detached report");
            assertEquals(true, field(report, "executionCompleted"));
            assertEquals(true, field(report, "executionSucceeded"));
            assertEquals(0, field(report, "passed"));
            assertEquals(1, field(report, "failed"));
            assertEquals(false, field(report, "assertionsPassed"));
            assertFalse(fixture.root.isActiveFailed(ScriptType.TEST));
        }
    }

    @Test
    void executionWithoutAssertionsDoesNotClaimAnAssertionPass() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("empty.mjs", "export const loaded = true;\n");
            fixture.root.runTests();
            Object report = fixture.report();
            assertNotNull(report, "zero assertions still need an explicit execution result");
            assertEquals(true, field(report, "executionCompleted"));
            assertEquals(true, field(report, "executionSucceeded"));
            assertEquals(0, field(report, "passed"));
            assertEquals(0, field(report, "failed"));
            assertNull(field(report, "assertionsPassed"), "zero assertions are not a vacuous green result");
        }
    }

    @Test
    void successfulEntriesAggregateAndRepeatedAttemptsDoNotReuseLifetimeCounts() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("one.mjs", "Test.pass('one');\n");
            fixture.write("two.mjs", "Test.pass('two');\n");
            var first = fixture.root.runTests();
            assertEquals(new TestRunReport(true, true, 2, 0), first.testRunReport());
            assertTrue(first.testRunReport().overallPassed());
            assertSame(first.testRunReport(), fixture.root.latestTestRunReport());

            fixture.write("two.mjs", "export const noAssertions = true;\n");
            var second = fixture.root.runTests();
            assertEquals(new TestRunReport(true, true, 1, 0), second.testRunReport());
            assertEquals(3, fixture.assertions.passed(), "the existing Test binding keeps its lifetime counts");
            assertEquals(2, first.testRunReport().passed(), "older detached snapshots stay immutable");
        }
    }

    @Test
    void anUncaughtAssertionPublishesTheFailedAttemptAndRetainsTheHealthyGeneration() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("entry.mjs", "Test.pass('baseline');\n");
            var baseline = fixture.root.runTests();
            long generation = fixture.root.scriptManagerOf(ScriptType.TEST).generationId();
            fixture.write("entry.mjs", "Test.fail('uncaught assertion');\n");

            assertThrows(NekoReloadException.class, fixture.root::runTests);

            var failed = fixture.root.latestTestRunReport();
            assertEquals(new TestRunReport(true, false, 0, 1), failed);
            assertEquals(false, failed.assertionsPassed());
            assertFalse(failed.overallPassed());
            assertEquals(generation, fixture.root.scriptManagerOf(ScriptType.TEST).generationId());
            assertFalse(fixture.root.isActiveFailed(ScriptType.TEST));
            assertEquals(new TestRunReport(true, true, 1, 0), baseline.testRunReport());

            fixture.write("entry.mjs", "Test.pass('recovered');\n");
            assertEquals(new TestRunReport(true, true, 1, 0), fixture.root.runTests().testRunReport());
        }
    }

    @Test
    void anOrdinaryEntryFailureIsNotAnAssertionPass() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("error.mjs", "throw Error('entry failed without assertions');\n");
            assertThrows(NekoReloadException.class, fixture.root::runTests);
            var report = fixture.root.latestTestRunReport();
            assertEquals(new TestRunReport(true, false, 0, 0), report);
            assertNull(report.assertionsPassed());
            assertFalse(report.overallPassed());
        }
    }

    @Test
    void notConfiguredKeepsTheExistingResultWithoutInventingAnAttempt() throws Exception {
        try (var fixture = new Fixture(gameDirectory, false, true)) {
            var result = fixture.root.runTests();
            assertFalse(result.isConfigured());
            assertFalse(result.isCompleted());
            assertNull(result.testRunReport());
            assertNull(fixture.root.latestTestRunReport());
            assertEquals(new NekoRuntimeRoot.TestRunResult(true, true), NekoRuntimeRoot.TestRunResult.completed());
        }
    }

    @Test
    void aConfiguredEnvironmentWithoutTheBuiltinBindingReportsNoAssertions() throws Exception {
        try (var fixture = new Fixture(gameDirectory, true, false)) {
            fixture.write("plain.mjs", "export const plain = true;\n");
            var report = fixture.root.runTests().testRunReport();
            assertEquals(new TestRunReport(true, true, 0, 0), report);
            assertNull(report.assertionsPassed());
            assertFalse(report.overallPassed());
        }
    }

    @Test
    void existingReadyTimerDrainContributesAssertionsToTheSameAttempt() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("timer.mjs", "setTimeout(() => Test.pass('ready timer'), 0);\n");
            assertEquals(new TestRunReport(true, true, 1, 0), fixture.root.runTests().testRunReport());
        }
    }

    @Test
    void topLevelAwaitAssertionsAreObservedAfterEntryCompletion() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("awaited.mjs", "await Promise.resolve(); Test.pass('after await');\n");
            assertEquals(new TestRunReport(true, true, 1, 0), fixture.root.runTests().testRunReport());
        }
    }

    @Test
    void legacyAndVersionedPackAssertionsShareOneAttemptReport() throws Exception {
        try (var fixture = new Fixture(gameDirectory)) {
            fixture.write("legacy.mjs", "Test.pass('legacy assertion');\n");
            Path pack = fixture.paths.packsRoot().resolve("test-report");
            Files.createDirectories(pack.resolve("test_scripts"));
            Files.writeString(pack.resolve("manifest.json"),
                    "{\"formatVersion\":1,\"id\":\"test-report\",\"version\":\"1.0.0\",\"entries\":{\"test\":[\"main.mjs\"]}}");
            Files.writeString(pack.resolve("test_scripts/main.mjs"),
                    "shared.packReportExecuted = true; try { Test.fail('caught pack assertion'); } catch (error) {}\n");
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshGlobalPacks();
            assertTrue(com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().allPacks().stream()
                    .anyMatch(source -> source.id().equals("test-report")), "the versioned source must be discovered");
            var report = fixture.root.runTests().testRunReport();
            assertEquals(Boolean.TRUE, fixture.root.globalState().sharedStore().get("packReportExecuted"),
                    "the pack entry must actually execute before its assertion counts are checked");
            assertEquals(new TestRunReport(true, true, 1, 1), report);
            assertEquals(false, report.assertionsPassed());
            assertFalse(report.overallPassed());
        }
    }

    private static Object field(Object report, String accessor) throws Exception {
        return report.getClass().getMethod(accessor).invoke(report);
    }

    private static final class Fixture implements AutoCloseable {
        final NekoJSPaths paths;
        final Engine engine = Engine.newBuilder().build();
        final TestJS assertions = new TestJS();
        final NekoRuntimeRoot root;
        final Object previousPaths;

        Fixture(Path gameDirectory) throws Exception {
            this(gameDirectory, true, true);
        }

        Fixture(Path gameDirectory, boolean configure, boolean includeAssertions) throws Exception {
            paths = NekoJSPaths.fromGameDir(gameDirectory);
            var field = NekoJSPaths.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            previousPaths = field.get(null);
            field.set(null, paths);
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshGlobalPacks();
            Files.createDirectories(paths.testScripts());
            var config = SandboxConfig.defaultConfig();
            var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            var tracker = new DefaultErrorTracker(paths, config);
            IPluginRuntime plugins = (IPluginRuntime) Proxy.newProxyInstance(IPluginRuntime.class.getClassLoader(),
                    new Class<?>[]{IPluginRuntime.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("bindings")) return includeAssertions ? Map.of("Test", Binding.of("Test", assertions)) : Map.of();
                        if (method.getReturnType() == Map.class) return Map.of();
                        if (method.getReturnType() == List.class) return List.of();
                        return null;
                    });
            var cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
            var core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            var sandbox = new NekoSandboxFactory(core, paths, compilers, plugins, cache);
            var properties = new ScriptPropertyRegistry.Impl();
            properties.register(ScriptProperty.AFTER);
            properties.register(ScriptProperty.MODLOADED);
            properties.register(ScriptProperty.DISABLE);
            properties.register(ScriptProperty.PRIORITY);
            properties.freeze();
            root = new NekoRuntimeRoot(core, plugins, ScriptEventBridge.EMPTY, properties, sandbox, cache);
            if (configure) root.createScriptManager(ScriptType.TEST).discoverScripts();
        }

        void write(String name, String source) throws Exception { Files.writeString(paths.testScripts().resolve(name), source); }

        TestRunReport report() { return root.latestTestRunReport(); }

        @Override public void close() throws Exception {
            try { root.close(); }
            finally {
                try { engine.close(); }
                finally {
                    var field = NekoJSPaths.class.getDeclaredField("INSTANCE");
                    field.setAccessible(true);
                    field.set(null, previousPaths);
                    com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshGlobalPacks();
                }
            }
        }
    }
}
