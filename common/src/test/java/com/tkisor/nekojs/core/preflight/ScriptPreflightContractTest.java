package com.tkisor.nekojs.core.preflight;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.preflight.ScriptPreflight;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.*;
import com.tkisor.nekojs.api.preflight.ScriptPreflightRegistry;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin;
import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.core.plugin.ScriptPreflightsPoint;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScriptPreflightContractTest {
    private static final String ID = "fixture:preflight";
    private static final String CAPABILITY = "fixture:semantic";
    private static final Diagnostic FINDING = new Diagnostic("fixture:rule", Severity.WARNING,
            "Example finding", new Position(1, 1, 0), "fixture-one");
    private static final String MAP = """
            {"version":3,"sources":["authored.py"],"mappings":"AAEE"}
            """;

    @TempDir Path root;

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void pluginPointFreezesDescriptorsAndDoesNotOpenProviders() {
        var opens = new AtomicInteger();
        var capturedRegistry = new AtomicReference<ScriptPreflightRegistry>();
        var revision = new AtomicReference<>(descriptor("first"));
        ScriptPreflight provider = new ScriptPreflight() {
            @Override public Descriptor descriptor() { return revision.get(); }
            @Override public Worker open() {
                opens.incrementAndGet();
                return request -> List.of();
            }
        };
        ScriptPreflightsPoint.Contributor contributor = new ScriptPreflightsPoint.Contributor() {
            @Override public void registerScriptPreflights(ScriptPreflightRegistry registry) {
                capturedRegistry.set(registry);
                registry.register(provider);
            }
        };
        var runtime = NekoPluginBootstrap.bootstrap(List.of(new NekoCommonBuiltinPlugin(),
                new NekoJSPlugin() {}, contributor), new ScriptPropertyRegistry.Impl());
        var catalog = (ScriptPreflightCatalog) runtime.extensionProducts().get(ScriptPreflightsPoint.ID);
        assertEquals(Set.of(ID, BuiltinJavaScriptPreflight.ID), catalog.descriptors().keySet());
        revision.set(descriptor("second"));
        assertEquals("first", catalog.find(ID).orElseThrow().revision());
        assertEquals(0, opens.get());
        assertThrows(UnsupportedOperationException.class, () -> catalog.descriptors().clear());
        assertThrows(IllegalStateException.class, () -> capturedRegistry.get().register(provider));
    }

    @Test
    void freezeRejectsRegistrationStillReadingItsDescriptor() throws Exception {
        var collector = new ScriptPreflightCatalog.Collector();
        var descriptorStarted = new CountDownLatch(1);
        var releaseDescriptor = new CountDownLatch(1);
        var registration = new CompletableFuture<Throwable>();
        Thread.startVirtualThread(() -> {
            try {
                collector.register(new ScriptPreflight() {
                    @Override public Descriptor descriptor() {
                        descriptorStarted.countDown();
                        try {
                            if (!releaseDescriptor.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("Freeze did not complete while the descriptor was blocked");
                            }
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                        return ScriptPreflightContractTest.descriptor("late");
                    }
                    @Override public Worker open() { return ignored -> List.of(); }
                });
                registration.complete(null);
            } catch (Throwable failure) {
                registration.complete(failure);
            }
        });
        ScriptPreflightCatalog frozen;
        try {
            assertTrue(descriptorStarted.await(10, TimeUnit.SECONDS));
            frozen = collector.freeze();
            assertTrue(frozen.descriptors().isEmpty());
        } finally {
            releaseDescriptor.countDown();
        }
        assertInstanceOf(IllegalStateException.class, registration.get(10, TimeUnit.SECONDS));
        assertSame(frozen, collector.freeze());
    }

    @Test
    void rejectsDuplicateIdsAndIncompatibleRequestsBeforeOpening() throws Exception {
        var opens = new AtomicInteger();
        var collector = new ScriptPreflightCatalog.Collector();
        collector.register(provider("one", () -> {
            opens.incrementAndGet();
            return request -> List.of();
        }));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> collector.register(provider("two", () -> request -> List.of())))
                .getMessage().contains(ID));
        try (var runner = new ScriptPreflightRunner(collector.freeze(), new SourceMapRegistry(root))) {
            assertThrows(IllegalArgumentException.class, () -> runner.run(ID, request(Scope.BINDINGS_ONLY, true, MAP)));
            assertThrows(IllegalArgumentException.class, () -> runner.run(ID, request(Scope.FULL, false, MAP)));
            var descriptor = descriptor("one");
            assertFalse(descriptor.supports(Stage.OUTPUT, Scope.FULL,
                    new FrontendSemantics.Input(FrontendSemantics.GENERATED_TEXT, "fixture:foreign"), Set.of(CAPABILITY)));
            assertEquals(0, opens.get());
        }
    }

    @Test
    void mapsThroughCurrentArtifactAndUsesGeneratedCoordinatesIfUnmapped() throws Exception {
        var maps = new SourceMapRegistry(root);
        var request = request(Scope.FULL, true, MAP);
        maps.register(request.path().toString(), MAP.replace("authored.py", "stale.py"));
        try (var runner = new ScriptPreflightRunner(catalog(provider("one", () -> ignored -> List.of(FINDING))), maps)) {
            var result = runner.run(ID, request).getFirst();
            assertEquals(Stage.OUTPUT, result.stage());
            assertEquals(Scope.FULL, result.scope());
            assertEquals("one", result.providerRevision());
            assertEquals(FINDING, result.diagnostic());
            assertEquals(new ScriptPreflightRunner.AuthoredPosition("server_scripts/authored.py",
                    new Position(3, 3, -1)), result.authoredPosition());
            assertNull(runner.run(ID, request(Scope.FULL, true, null)).getFirst().authoredPosition());
            assertEquals("server_scripts/stale.py", maps.getMappedPosition(request.path().toString(), 1, 1).path);
        }
    }

    @Test
    void outputFindingsRespectExplicitUnmappedSegments() throws Exception {
        String map = """
                {"version":3,"sources":["authored.py"],"mappings":"AAAA,C,CAAC"}
                """;
        var maps = new SourceMapRegistry(root);
        var request = request(Scope.FULL, true, map);
        maps.register(request.path().toString(), map);
        try (var runner = new ScriptPreflightRunner(catalog(provider("one", () -> ignored -> List.of(
                findingAt(1), findingAt(2), findingAt(3)))), maps)) {
            var reports = runner.run(ID, request);
            assertEquals(new Position(1, 1, -1), reports.get(0).authoredPosition().position());
            assertNull(reports.get(1).authoredPosition());
            assertEquals(new Position(1, 2, 1), reports.get(1).diagnostic().position());
            assertEquals(new Position(1, 2, -1), reports.get(2).authoredPosition().position());
        }
        // The existing stack-trace lookup deliberately keeps its older nearest-mapping behavior.
        assertEquals(1, maps.getMappedPosition(request.path().toString(), 1, 2).column);
        assertNotNull(maps.getMappedPosition(request.path().toString(), 1, 2).path);
    }

    private static Diagnostic findingAt(int column) {
        return new Diagnostic("fixture:rule", Severity.WARNING, "Example finding",
                new Position(1, column, column - 1), null);
    }

    @Test
    void preservesProviderFailureAndRetriesInsteadOfCachingSuccess() throws Exception {
        var calls = new AtomicInteger();
        var closes = new AtomicInteger();
        var error = new IOException("fixture failure");
        var runner = new ScriptPreflightRunner(catalog(provider("revision-a", () -> new Worker() {
            @Override public List<Diagnostic> check(Request request) throws IOException {
                if (calls.getAndIncrement() == 0) throw error;
                return List.of(FINDING);
            }
            @Override public void close() { closes.incrementAndGet(); }
        })), new SourceMapRegistry(root));
        var request = request(Scope.FULL, true, null);
        try (runner) {
            var failure = assertThrows(ScriptPreflightRunner.ProviderFailure.class, () -> runner.run(ID, request));
            assertEquals(ID, failure.providerId());
            assertEquals(Stage.OUTPUT, failure.stage());
            assertSame(error, failure.getCause());
            assertEquals(FINDING, runner.run(ID, request).getFirst().diagnostic());
        }
        runner.close();
        assertEquals(2, calls.get());
        assertEquals(1, closes.get());
        assertThrows(IllegalStateException.class, () -> runner.run(ID, request));
        try (var next = new ScriptPreflightRunner(catalog(provider("revision-b", () -> ignored -> List.of(FINDING))),
                new SourceMapRegistry(root))) {
            assertEquals("revision-b", next.run(ID, request).getFirst().providerRevision());
        }
    }

    @Test
    void openingAndRunningAttributeNonFatalErrorsToTheProvider() throws Exception {
        var request = request(Scope.FULL, true, null);
        var ioError = new IOError(new IOException("fixture I/O failure"));
        for (boolean failsOnOpen : List.of(true, false)) {
            var failing = provider("one", () -> {
                if (failsOnOpen) throw ioError;
                return ignored -> { throw ioError; };
            });
            try (var runner = new ScriptPreflightRunner(catalog(failing), new SourceMapRegistry(root))) {
                var failure = assertThrows(ScriptPreflightRunner.ProviderFailure.class, () -> runner.run(ID, request));
                assertEquals(ID, failure.providerId());
                assertEquals(Stage.OUTPUT, failure.stage());
                assertEquals(Scope.FULL, failure.scope());
                assertSame(ioError, failure.getCause());
                assertTrue(failure.getMessage().contains(failsOnOpen ? "failed to open" : "failed to run"));
            }
        }
    }

    @Test
    void openingAndRunningRethrowFatalErrorsWithoutWrapping() throws Exception {
        var request = request(Scope.FULL, true, null);
        for (Error fatal : List.of(new ThreadDeath(), new InternalError("fixture fatal error"))) {
            for (boolean failsOnOpen : List.of(true, false)) {
                var failing = provider("one", () -> {
                    if (failsOnOpen) throw fatal;
                    return ignored -> { throw fatal; };
                });
                try (var runner = new ScriptPreflightRunner(catalog(failing), new SourceMapRegistry(root))) {
                    assertSame(fatal, assertThrows(fatal.getClass(), () -> runner.run(ID, request)));
                }
            }
        }
    }

    @Test
    void closeAttemptsEveryWorkerAndRetainsAttributionForFailures() throws Exception {
        var first = new IOError(new IOException("first close"));
        var second = new IOException("second close");
        var closedLast = new AtomicInteger();
        var runner = openWorkers(
                closingProvider("fixture:first", () -> { throw first; }),
                closingProvider("fixture:second", () -> { throw second; }),
                closingProvider("fixture:last", closedLast::incrementAndGet));

        var failure = assertThrows(ScriptPreflightRunner.ProviderFailure.class, runner::close);
        assertEquals("fixture:first", failure.providerId());
        assertSame(first, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        var suppressed = assertInstanceOf(ScriptPreflightRunner.ProviderFailure.class, failure.getSuppressed()[0]);
        assertEquals("fixture:second", suppressed.providerId());
        assertSame(second, suppressed.getCause());
        assertEquals(1, closedLast.get());
        runner.close();
        assertEquals(1, closedLast.get());
    }

    @Test
    void closeRethrowsFatalVmErrorAfterClosingOtherWorkers() throws Exception {
        var fatal = new InternalError("fixture fatal error");
        var ordinary = new IOException("ordinary close failure");
        var closedLast = new AtomicInteger();
        var runner = openWorkers(
                closingProvider("fixture:ordinary", () -> { throw ordinary; }),
                closingProvider("fixture:fatal", () -> { throw fatal; }),
                closingProvider("fixture:last", closedLast::incrementAndGet));

        assertSame(fatal, assertThrows(InternalError.class, runner::close));
        assertEquals(1, closedLast.get());
        assertEquals(1, fatal.getSuppressed().length);
        var suppressed = assertInstanceOf(ScriptPreflightRunner.ProviderFailure.class, fatal.getSuppressed()[0]);
        assertEquals("fixture:ordinary", suppressed.providerId());
        assertSame(ordinary, suppressed.getCause());
        runner.close();
        assertEquals(1, closedLast.get());
    }

    private ScriptPreflightRunner openWorkers(ScriptPreflight... providers) throws Exception {
        var collector = new ScriptPreflightCatalog.Collector();
        for (var provider : providers) collector.register(provider);
        var runner = new ScriptPreflightRunner(collector.freeze(), new SourceMapRegistry(root));
        for (var provider : providers) {
            runner.run(provider.descriptor().id(), request(Scope.FULL, true, null));
        }
        return runner;
    }

    private static ScriptPreflight closingProvider(String id, AutoCloseable close) {
        return new ScriptPreflight() {
            @Override public Descriptor descriptor() {
                return new Descriptor(id, "fixture", Map.of(Stage.OUTPUT, Set.of(FrontendSemantics.JAVASCRIPT_OUTPUT)),
                        Set.of(Scope.FULL), Set.of(CAPABILITY));
            }
            @Override public Worker open() {
                return new Worker() {
                    @Override public List<Diagnostic> check(Request request) { return List.of(); }
                    @Override public void close() throws Exception { close.close(); }
                };
            }
        };
    }

    private static ScriptPreflightCatalog catalog(ScriptPreflight provider) {
        var collector = new ScriptPreflightCatalog.Collector();
        collector.register(provider);
        return collector.freeze();
    }

    private static Descriptor descriptor(String revision) {
        return new Descriptor(ID, revision, Map.of(Stage.OUTPUT, Set.of(FrontendSemantics.JAVASCRIPT_OUTPUT)),
                Set.of(Scope.FULL), Set.of(CAPABILITY));
    }

    private static ScriptPreflight provider(String revision, WorkerFactory factory) {
        return new ScriptPreflight() {
            @Override public Descriptor descriptor() { return ScriptPreflightContractTest.descriptor(revision); }
            @Override public Worker open() throws Exception { return factory.open(); }
        };
    }

    private Request request(Scope scope, boolean capabilityAvailable, String map) {
        return new Request(root.resolve("server_scripts/generated.js"), ScriptType.SERVER, "fixture:frontend",
                Stage.OUTPUT, scope, FrontendSemantics.JAVASCRIPT_OUTPUT,
                capabilityAvailable ? Set.of(CAPABILITY) : Set.of(), "performCheck();", map, Map.of(), Set.of());
    }

    @FunctionalInterface
    private interface WorkerFactory {
        Worker open() throws Exception;
    }
}
