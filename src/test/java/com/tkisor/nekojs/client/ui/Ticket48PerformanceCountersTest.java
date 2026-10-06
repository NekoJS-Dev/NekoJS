//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket48PerformanceCountersTest {
    @Test
    void retainedHostExposesBuildLayoutReconcilePaintResizeAndCleanupCounters() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.value = UI.createSignal('counter');
                    globalThis.root = UI.createRoot(() => UI.element('label', {
                      id: 'counter-label', text: value.get(), width: 40, height: 12
                    }), host, { id: 'counter-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    value.set('updated');
                    root.resize({ width: 120, height: 80, profile: 2 });
                    """);
            var failed = fixture.adapter.begin();
            assertThrows(IllegalArgumentException.class,
                    () -> failed.update("missing", "label", "missing", Map.of()));
            failed.rollback();
            fixture.context.eval("js", "value.set('retry')");
            fixture.adapter.resize(120, 80);
            fixture.paint();

            Map<String, Object> beforeCleanup = fixture.adapter.performanceCounters();
            assertEquals(1L, ((Number) beforeCleanup.get("initialBuild")).longValue());
            assertTrue(((Number) beforeCleanup.get("layout")).longValue() >= 2);
            assertTrue(((Number) beforeCleanup.get("reconcile")).longValue() >= 1);
            assertTrue(((Number) beforeCleanup.get("resize")).longValue() >= 1);
            assertTrue(((Number) beforeCleanup.get("paint")).longValue() >= 1);

            fixture.globals.close();
            Map<String, Object> afterCleanup = fixture.adapter.performanceCounters();
            assertEquals(beforeCleanup.get("reconcile"), afterCleanup.get("reconcile"));
            assertTrue(((Number) afterCleanup.get("cleanup")).longValue() >= 1);
        }
    }

    @Test
    void hostFailuresAreIncludedInDiagnosticsCounter() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", "host.bindRoot({ id: 'bad-root', resize() { throw new Error('resize boom') } })");
            fixture.adapter.resize(120, 80);

            Map<String, Object> counters = fixture.adapter.performanceCounters();
            assertTrue(((Number) counters.get("diagnostics")).longValue() >= 1);
        }
    }
}
//?}
