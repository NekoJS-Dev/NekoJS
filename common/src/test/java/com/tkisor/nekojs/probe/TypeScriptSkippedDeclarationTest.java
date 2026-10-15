package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.error.Diagnostics;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptSkippedDeclarationTest {
    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void unavailableForeignDeclarationIsReportedWithoutInventingItsBody(@TempDir Path directory) throws Exception {
        var sinkField = Diagnostics.class.getDeclaredField("sink");
        sinkField.setAccessible(true);
        Diagnostics.Sink previous = (Diagnostics.Sink) sinkField.get(null);
        var events = new CopyOnWriteArrayList<Diagnostics.Event>();
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ClassLoader unavailable = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(URI.class.getName())) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        var pool = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "owned-probe-skipped-declaration");
            thread.setContextClassLoader(unavailable);
            return thread;
        });
        try {
            Diagnostics.install(events::add);
            var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
            var config = new ProbeConfig(true, ".neko_probe",
                    new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 5, "SMART"));
            var context = new ProbeContext.Of(snapshot, List.of(URI.class), config,
                    NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve(".neko_probe/typescript"),
                    null, ProbeOverrides.empty(), pool);
            Map<String, String> files = new TypeScriptProbeBackend().render(context);
            assertFalse(files.values().stream().anyMatch(body -> body.contains("export class $URI")));
            var skipped = events.stream().filter(event -> event.message().contains("[NEKO-4038]")).toList();
            assertEquals(1, skipped.size());
            assertEquals("probe-typescript", skipped.getFirst().scope());
            assertEquals(Diagnostics.Severity.WARN, skipped.getFirst().severity());
            assertTrue(skipped.getFirst().message().contains("class=java.net.URI"));
            assertInstanceOf(ClassNotFoundException.class, skipped.getFirst().throwable());
        } finally {
            try {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                Diagnostics.install(previous);
            }
        }
    }
}
