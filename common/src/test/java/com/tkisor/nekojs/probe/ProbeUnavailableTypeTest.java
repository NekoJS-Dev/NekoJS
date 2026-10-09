package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProbeUnavailableTypeTest {

    private static final String FIXTURE_PREFIX = ProbeLinkageFixture.class.getName();

    private enum MissingType { ABSENT, REJECTED, FATAL }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void missingSignatureKeepsAvailableOutputAndReportsBothBackendWarnings(@TempDir Path directory) throws Exception {
        assertAvailableOutputWithWarning(directory, MissingType.ABSENT, false);
    }

    @Test
    void loaderRejectedSignatureKeepsAvailableOutputAndReportsBothBackendWarnings(@TempDir Path directory) throws Exception {
        assertAvailableOutputWithWarning(directory, MissingType.REJECTED, false);
    }

    @Test
    void missingGenericSignatureKeepsAvailableOutputAndReportsBothBackendWarnings(@TempDir Path directory) throws Exception {
        assertAvailableOutputWithWarning(directory, MissingType.ABSENT, true);
    }

    @Test
    void missingSamSignatureKeepsAvailableOutputAndReportsBothBackendWarnings(@TempDir Path directory) throws Exception {
        assertAvailableOutputWithWarning(directory, MissingType.ABSENT, false, true);
    }

    @Test
    void loaderRejectedSamSignatureKeepsAvailableOutputAndReportsBothBackendWarnings(@TempDir Path directory) throws Exception {
        assertAvailableOutputWithWarning(directory, MissingType.REJECTED, false, true);
    }

    @Test
    void fatalSamReflectionIsNotReportedAsAnUnavailableAlias() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(fixtureLoader(MissingType.FATAL));
            var aliases = new com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator(
                    new com.tkisor.nekojs.probe.types.TypeAliasRegistry());
            assertThrows(OutOfMemoryError.class, () -> aliases.prepare(
                    java.util.Set.of(ProbeLinkageFixture.ClientCallback.class.getName()), java.util.Set.of()));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void virtualMachineFailureIsNotReportedAsPartialSuccess() throws Exception {
        Class<?> host = fixtureLoader(MissingType.FATAL).loadClass(ProbeLinkageFixture.Host.class.getName());
        assertThrows(OutOfMemoryError.class,
                () -> ProbeCoordinator.collectClasses(snapshot(host), ProbeConfig.defaultConfig()));
    }

    @Test
    void fatalSharedReflectionCancelsOtherSubmittedTypes() throws Exception {
        ClassLoader loader = fixtureLoader(MissingType.FATAL);
        Class<?> extension = loader.loadClass(ProbeLinkageFixture.Extension.class.getName());
        List<Future<?>> submitted = new CopyOnWriteArrayList<>();
        CountDownLatch releaseRemaining = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void execute(Runnable task) {
                submitted.add((Future<?>) task);
                super.execute(task);
            }

            @Override
            protected void beforeExecute(Thread worker, Runnable task) {
                if (task == submitted.getFirst()) return;
                try {
                    releaseRemaining.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        try {
            assertThrows(OutOfMemoryError.class, () -> ProbeIrBuilder.buildAndMutateIr(
                    List.of(extension, ProbeLinkageFixture.Host.class, ProbeLinkageFixture.GenericHost.class),
                    Map.of(), pool, new ArrayList<>()));
            assertEquals(3, submitted.size());
            assertTrue(submitted.subList(1, submitted.size()).stream().allMatch(Future::isCancelled),
                    "Fatal reflection must cancel the other submitted types before returning");
        } finally {
            releaseRemaining.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void assertAvailableOutputWithWarning(Path directory, MissingType missingType, boolean generic) throws Exception {
        assertAvailableOutputWithWarning(directory, missingType, generic, false);
    }

    private static void assertAvailableOutputWithWarning(Path directory, MissingType missingType,
                                                        boolean generic, boolean callback) throws Exception {
        ClassLoader loader = fixtureLoader(missingType);
        Class<?> host = loader.loadClass((callback ? ProbeLinkageFixture.CallbackHost.class
                : generic ? ProbeLinkageFixture.GenericHost.class : ProbeLinkageFixture.Host.class).getName());
        Class<?> extension = loader.loadClass((callback ? ProbeLinkageFixture.ClientCallback.class
                : generic ? ProbeLinkageFixture.GenericExtension.class : ProbeLinkageFixture.Extension.class).getName());
        if (generic) {
            assertThrows(TypeNotPresentException.class,
                    () -> extension.getDeclaredMethod("clientModels").getGenericReturnType());
        } else if (missingType == MissingType.REJECTED) {
            assertThrows(IllegalStateException.class, extension::getDeclaredMethods);
        } else {
            assertThrows(NoClassDefFoundError.class, extension::getDeclaredMethods);
        }
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        ProbeCoordinator coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            List<ProbeBackend.GenerateResult> results = coordinator.runProbe(snapshot(host),
                    List.of(new TypeScriptProbeBackend(), new PythonProbeBackend()));
            assertEquals(2, results.size());
            for (ProbeBackend.GenerateResult result : results) {
                assertTrue(result.success(), result::message);
                assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains(extension.getName())
                        && warning.replace('/', '.').contains(ProbeLinkageFixture.Missing.class.getName())), result.warnings()::toString);
            }
            Path ts = directory.resolve(".neko_probe/typescript/@package/com/tkisor/nekojs/probe/index.d.ts");
            assertTrue(Files.readString(ts).contains("status"));
            try (var stubs = Files.walk(directory.resolve(".neko_probe/python"))) {
                assertTrue(stubs.filter(path -> path.toString().endsWith(".pyi"))
                        .anyMatch(ProbeUnavailableTypeTest::containsStatus));
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static NekoScriptCatalogSnapshot snapshot(Class<?> host) {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, host, false)),
                List.of(EventCatalogEntry.of("FixtureEvents", "host", ScriptType.SERVER, host, null, false, false)),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static boolean containsStatus(Path path) {
        try {
            return Files.readString(path).contains("def status(");
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ClassLoader fixtureLoader(MissingType missingType) {
        return new ClassLoader(ProbeUnavailableTypeTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith(FIXTURE_PREFIX)) return super.loadClass(name, resolve);
                if (name.equals(ProbeLinkageFixture.Missing.class.getName())) {
                    if (missingType == MissingType.FATAL) throw new OutOfMemoryError("Fixture VM failure");
                    if (missingType == MissingType.REJECTED) throw new IllegalStateException("Fixture distribution rejects " + name);
                    throw new ClassNotFoundException(name);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (InputStream input = getParent().getResourceAsStream(resource)) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
    }
}
