package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.core.node.NekoNodeTimers;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class PackResourceStabilityTest {
    // Platform log appenders retain a Windows handle until the test JVM exits.
    @TempDir(cleanup = CleanupMode.NEVER) Path temporary;

    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 8})
    void independentOwnersHaveBoundedContextCountsDuringReplacement(int packs) throws Exception {
        int baselineContexts = registeredContexts();
        try (var fixture = new PackActivationTest.Fixture(temporary)) {
            fixture.root.reload(ScriptType.SERVER);
            long legacyHeap = observedHeap();
            installPacks(fixture, packs);
            var probe = new ResourceProbe();
            fixture.root.registerDomainCollector(probe);
            fixture.root.reload(ScriptType.SERVER);
            List<Resources> current = probe.take();
            assertEquals(packs + 1, current.size());
            assertLive(current, baselineContexts + packs + 1);
            long activeHeap = observedHeap();
            probe.measureHeap = true;
            fixture.root.reload(ScriptType.SERVER);
            List<Resources> replacement = probe.take();
            assertEquals(baselineContexts + 2 * (packs + 1), probe.peakRegistered);
            assertReleased(current);
            assertLive(replacement, baselineContexts + packs + 1);
            assertEvents(fixture, packs);
            System.out.printf("[PackResources] packs=%d activeContexts=%d peakContexts=%d legacyHeapBytes=%d activeHeapBytes=%d candidateHeapBytes=%d%n",
                    packs, replacement.size(), probe.peakRegistered - baselineContexts,
                    legacyHeap, activeHeap, probe.candidateHeap);
            fixture.root.close();
            assertReleased(replacement);
            assertEquals(baselineContexts, registeredContexts());
        }
    }

    @Test
    void fiftyFourPackReplacementsReleaseOldContextsListenersAndSchedulers() throws Exception {
        int baselineContexts = registeredContexts();
        List<WeakReference<Context>> retired = new ArrayList<>();
        long[] heapWindows = new long[5];
        try (var fixture = new PackActivationTest.Fixture(temporary)) {
            installPacks(fixture, 4);
            var probe = new ResourceProbe();
            fixture.root.registerDomainCollector(probe);
            fixture.root.reload(ScriptType.SERVER);
            List<Resources> current = probe.take();
            for (int reload = 1; reload <= 50; reload++) {
                current = replaceAndRelease(fixture, probe, current, retired, baselineContexts);
                if (reload % 10 == 0) {
                    long heap = observedHeap();
                    heapWindows[reload / 10 - 1] = heap;
                    long collected = retired.stream().filter(reference -> reference.get() == null).count();
                    System.out.printf("[PackResources] reloads=%d packs=4 activeContexts=5 peakContexts=10 heapBytes=%d retiredContexts=%d observedCollected=%d%n",
                            reload, heap, retired.size(), collected);
                }
            }
            // Match the existing reload memory test's allowance for one-time Graal/JIT growth.
            assertTrue(heapWindows[4] <= heapWindows[0] + 32L * 1024 * 1024,
                    "Retained Java heap exceeded the existing 32 MiB reload allowance: " + java.util.Arrays.toString(heapWindows));
            fixture.root.close();
            assertReleased(current);
            assertEquals(baselineContexts, registeredContexts());
        }
    }

    @Test
    void repeatedDomainFailureReleasesCandidatesAndRetainsHealthyOwners() throws Exception {
        int baselineContexts = registeredContexts();
        try (var fixture = new PackActivationTest.Fixture(temporary)) {
            installPacks(fixture, 4);
            var probe = new ResourceProbe();
            fixture.root.registerDomainCollector(probe);
            fixture.root.reload(ScriptType.SERVER);
            List<Resources> healthy = probe.take();
            Map<String, Long> activations = fixture.activations();
            probe.reject = true;
            for (int failure = 1; failure <= 5; failure++) {
                var result = assertThrows(NekoReloadException.class, () -> fixture.root.reload(ScriptType.SERVER));
                assertTrue(result.getMessage().contains("resource-acceptance-rejection"), result.getMessage());
                List<Resources> rejected = probe.take();
                assertEquals(5, rejected.size());
                assertEquals(baselineContexts + 10, probe.peakRegistered);
                assertReleased(rejected);
                assertLive(healthy, baselineContexts + 5);
                assertEquals(activations, fixture.activations());
                assertEvents(fixture, 4);
                assertImmediateCallbacks(fixture, healthy, 4, failure == 1);
            }
            probe.reject = false;
            fixture.root.reload(ScriptType.SERVER);
            List<Resources> recovered = probe.take();
            assertReleased(healthy);
            assertLive(recovered, baselineContexts + 5);
            assertEvents(fixture, 4);
            assertImmediateCallbacks(fixture, recovered, 4, true);
            fixture.root.close();
            assertReleased(recovered);
            assertEquals(baselineContexts, registeredContexts());
            System.out.println("[PackResources] rejectedBatches=5 candidatesPerBatch=5 healthyContexts=5 recovery=PASS release=PASS");
        }
    }

    private static List<Resources> replaceAndRelease(PackActivationTest.Fixture fixture, ResourceProbe probe,
            List<Resources> previous, List<WeakReference<Context>> retired, int baselineContexts) throws Exception {
        fixture.root.reload(ScriptType.SERVER);
        List<Resources> current = probe.take();
        assertEquals(5, current.size());
        assertEquals(baselineContexts + 10, probe.peakRegistered);
        assertReleased(previous);
        previous.forEach(resource -> retired.add(new WeakReference<>(resource.context())));
        assertLive(current, baselineContexts + 5);
        assertEvents(fixture, 4);
        assertImmediateCallbacks(fixture, current, 4, true);
        return current;
    }

    private static void installPacks(PackActivationTest.Fixture fixture, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            String id = "pack" + index;
            fixture.pack(id, "", "", """
                    if (typeof privateOwner !== 'undefined') throw Error('owner context reused');
                    globalThis.privateOwner = '%s';
                    globalThis.privatePayload = new Array(4096).fill('payload');
                    FixtureEvents.pulse(event => Recorder.record('event:' + privateOwner));
                    setImmediate(() => Recorder.record('immediate:' + privateOwner));
                    setInterval(() => Recorder.record('interval:' + privateOwner), 3600000);
                    """.formatted(id));
        }
    }

    private static void assertEvents(PackActivationTest.Fixture fixture, int packs) {
        int[] before = new int[packs];
        for (int index = 0; index < packs; index++) before[index] = fixture.recorder.calls("event:pack" + index);
        fixture.pulse.post(new Object());
        for (int index = 0; index < packs; index++) assertEquals(before[index] + 1, fixture.recorder.calls("event:pack" + index));
    }

    private static void assertImmediateCallbacks(PackActivationTest.Fixture fixture, List<Resources> active,
            int packs, boolean expectedPending) throws Exception {
        // Wait for each scheduled immediate to reach the ready queue, without running guest code on its worker.
        if (expectedPending) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            for (Resources resource : active) {
                if (!resource.context().getBindings("js").hasMember("privateOwner")) continue;
                while (((java.util.Queue<?>) field(NekoNodeTimers.class, "ready").get(resource.timers())).isEmpty()
                        && System.nanoTime() < deadline) Thread.sleep(1);
                assertFalse(((java.util.Queue<?>) field(NekoNodeTimers.class, "ready").get(resource.timers())).isEmpty());
            }
        }
        int[] before = new int[packs];
        for (int index = 0; index < packs; index++) before[index] = fixture.recorder.calls("immediate:pack" + index);
        fixture.root.flushReadyNodeTimers(ScriptType.SERVER);
        for (int index = 0; index < packs; index++) {
            assertEquals(before[index] + (expectedPending ? 1 : 0), fixture.recorder.calls("immediate:pack" + index));
            assertEquals(0, fixture.recorder.calls("interval:pack" + index));
        }
    }

    private static void assertLive(List<Resources> resources, int expectedRegistered) throws Exception {
        assertEquals(expectedRegistered, registeredContexts());
        assertEquals(resources.size(), resources.stream().map(Resources::context).distinct().count());
        for (Resources resource : resources) {
            assertEquals(ScriptType.SERVER, ScriptContextRegistry.scriptTypeOf(resource.context()));
            assertNotNull(ScriptManager.from(resource.context()));
            assertFalse(resource.scheduler().isShutdown());
            assertEquals(42, resource.context().eval("js", "21*2").asInt());
        }
    }

    private static void assertReleased(List<Resources> resources) throws Exception {
        for (Resources resource : resources) {
            assertThrows(IllegalStateException.class, () -> resource.context().eval("js", "21*2"));
            assertThrows(IllegalStateException.class, () -> ScriptManager.from(resource.context()));
            assertNull(ScriptContextRegistry.scriptTypeOf(resource.context()));
            assertNull(ScriptContextRegistry.currentScriptIdOf(resource.context()));
            for (String name : List.of("contextToScriptType", "contextToScriptId", "contextToBindings", "lastWrittenScriptId")) {
                assertFalse(((Map<?, ?>) field(ScriptContextRegistry.class, name).get(null)).containsKey(resource.context()));
            }
            assertTrue(resource.scheduler().isTerminated());
            assertFalse(resource.timers().hasPendingCallbacks());
            assertDoesNotThrow(resource.timers()::flushReadyCallbacks);
            assertTrue(((Map<?, ?>) field(NekoNodeTimers.class, "tasks").get(resource.timers())).isEmpty());
            assertTrue(((java.util.Queue<?>) field(NekoNodeTimers.class, "ready").get(resource.timers())).isEmpty());
        }
    }

    private static int registeredContexts() throws Exception {
        return ((Map<?, ?>) field(ScriptManager.class, "CONTEXT_TO_MANAGER").get(null)).size();
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static long observedHeap() throws InterruptedException {
        // GC requests provide an observation window, not proof that every retired Context was collected.
        for (int attempt = 0; attempt < 3; attempt++) { System.gc(); Thread.sleep(50); }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record Resources(Context context, NekoNodeTimers timers, ScheduledExecutorService scheduler) {
        static Resources capture(Context context) throws Exception {
            NekoNodeRuntime runtime = context.getBindings("js").getMember("__nekoNodeRuntime").asHostObject();
            return new Resources(context, runtime.timers(),
                    (ScheduledExecutorService) field(NekoNodeTimers.class, "scheduler").get(runtime.timers()));
        }
    }

    private static final class ResourceProbe implements CandidateDomainCollector {
        private List<Resources> latest = List.of();
        private int peakRegistered;
        private boolean measureHeap;
        private long candidateHeap;
        private boolean reject;
        public String domain() { return "pack-resource-acceptance"; }
        public ScriptType scriptType() { return ScriptType.SERVER; }
        public void collect(Handle handle) {
            try {
                List<Resources> captured = new ArrayList<>();
                for (Context context : handle.candidateContexts()) captured.add(Resources.capture(context));
                latest = List.copyOf(captured);
                peakRegistered = registeredContexts();
                if (measureHeap) candidateHeap = observedHeap();
            } catch (Exception failure) {
                throw new IllegalStateException("Resource acceptance observation failed", failure);
            }
            if (reject) throw new IllegalStateException("resource-acceptance-rejection");
        }
        List<Resources> take() { List<Resources> result = latest; latest = List.of(); return result; }
    }
}
