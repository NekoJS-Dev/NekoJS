package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative cases for collection and freezing, plus epoch isolation:
 * <ul>
 *   <li>A Builder configuration callback that throws must not leave a half-built entry that
 *       drain could pick up: the builder is configured before it is stored;</li>
 *   <li>Changes to the underlying collection container after a drain must not modify the
 *       published snapshot ({@code DrainResult} is frozen);</li>
 *   <li>The read-only live view ({@code undrainedLiveView}) is a defensive copy, not a
 *       frozen result;</li>
 *   <li>A failed epoch leaves no process-level leftovers that could pollute the next
 *       startup: a new epoch starts from an empty repository;</li>
 *   <li>Mutable state inside an arbitrary Supplier is not claimed to be deeply immutable:
 *       the supplier is forwarded as is and its product is not snapshotted.</li>
 * </ul>
 */
class StartupRegistryEpochNegativesTest {

    private static final String NODE = "negative-test-node";

    @Test
    void throwingConfigCallbackLeavesNoDrainableHalfFinishedEntry() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin())) {
            script.context.eval("js", """
                    StartupEvents.registry('sound_event', event => {
                        event.create('mymod:complete', build => { build.fixedRange = 16 });
                        event.create('mymod:broken', build => {
                            build.fixedRange = 8;
                            throw new Error('script exploded mid-config');
                        });
                    });
                    StartupEvents.registry('minecraft:sound_event', event => {
                        event.create('mymod:next_listener', build => {});
                    });
                    """);
            runtime.collectOnce();
        }

        // Completed entries and subsequent listeners survive the configuration failure.
        var view = runtime.undrainedLiveView();
        assertEquals(List.of(Identifier.parse("mymod:complete"), Identifier.parse("mymod:next_listener")), view.get(Registries.SOUND_EVENT),
                "抛错的 builder 不入库；完整条目保留");

        RecordingSink sink = new RecordingSink();
        var drained = runtime.drainFor(Registries.SOUND_EVENT, sink);
        assertEquals(List.of("mymod:complete", "mymod:next_listener"),
                drained.registered().stream().map(record -> record.definition().toString()).toList());
        assertTrue(runtime.isFullyDrained(), "半成品没有残留到 drain 期");
    }

    @Test
    void postDrainCollectionChangesDoNotAlterThePublishedSnapshot() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin())) {
            script.context.eval("js", "StartupEvents.registry('sound_event', event => event.create('mymod:first', build => {}));");
            runtime.collectOnce();
        }
        var first = runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
        runtime.repository().add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:second")), "basic", "post-snapshot");

        assertEquals(1, first.registered().size());
        assertEquals("mymod:first", first.registered().get(0).definition().toString());
        assertThrows(UnsupportedOperationException.class, () -> first.registered().add(null));
        assertEquals("mymod:second", runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink())
                .registered().getFirst().definition().toString());
    }

    @Test
    void liveViewIsADefensiveCopyNotAFrozenResult() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        runtime.repository().add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:live")), "basic", "test");

        var view = runtime.undrainedLiveView();
        assertEquals(1, view.get(Registries.SOUND_EVENT).size());
        // Defensive copy: callers cannot change runtime state through the view. The inner lists are
        // immutable, and the map is a copy, so writes to it are never written back.
        assertThrows(UnsupportedOperationException.class, () -> view.get(Registries.SOUND_EVENT).clear());
        view.put(Registries.SOUND_EVENT, List.of());
        assertEquals(1, runtime.undrainedLiveView().get(Registries.SOUND_EVENT).size(),
                "live view 是副本；真实状态以 drain 结果快照为准");

        var drained = runtime.drainFor(Registries.SOUND_EVENT, new RecordingSink());
        assertNotSame(view, drained);
        assertTrue(runtime.undrainedLiveView().isEmpty(), "drain 后 live view 反映真实状态（空）");
        assertEquals(1, drained.registered().size(), "冻结结果在 DrainResult 里，不靠 live view 冒充");
    }

    @Test
    void failedEpochStagingDoesNotPolluteTheNextBoot() {
        StartupRegistryRuntime failedBoot = new StartupRegistryRuntime(NODE);
        failedBoot.repository().add(Registries.SOUND_EVENT,
                new SoundEventBuilder(Identifier.parse("mymod:stale")), "basic", "never-drained");
        List<String> diagnostics = new ArrayList<>();
        failedBoot.reportUndelivered(diagnostics::add);
        assertEquals(1, diagnostics.size(), "未交付内容在 epoch 收尾可诊断");
        assertTrue(diagnostics.get(0).contains("mymod:stale"));
        assertTrue(diagnostics.get(0).contains(NODE), "诊断带节点");
        assertFalse(failedBoot.isFullyDrained(), "失败 epoch 确有暂存（测试前提）");

        // Next boot: a new epoch starts from an empty repository, which is what the adapter's
        // beginBoot does, and the diagnostic log is dropped.
        StartupRegistryRuntime nextBoot = new StartupRegistryRuntime(NODE);
        assertTrue(nextBoot.isFullyDrained(), "新一轮启动不被上一轮的进程级暂存污染");
        assertTrue(nextBoot.undrainedLiveView().isEmpty());
        assertEquals(0, nextBoot.drainFor(Registries.SOUND_EVENT, new RecordingSink()).registered().size());
    }

    @Test
    void arbitrarySupplierMutableStateIsForwardedNotSnapshot() {
        // Mutable state inside a bare supplier is neither deep-copied nor snapshotted. The Runtime only
        // checks non-null and the type when it runs the supplier.
        java.util.List<String> box = new ArrayList<>(List.of("before"));
        RegistryRepository repository = new RegistryRepository();
        RegistryObjectBuilder<Object> supplier = new RegistryObjectBuilder<>(Identifier.parse("mymod:mutable")) {
            @Override
            public Object build() {
                return box.get(0);
            }
        };
        repository.add(Registries.SOUND_EVENT, supplier, "supplier", "test");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE, repository);

        RecordingSink sink = new RecordingSink();
        var first = runtime.drainFor(Registries.SOUND_EVENT, sink);
        box.set(0, "after");
        // A drained result is not replayed or rewritten when the supplier's internal state changes.
        assertEquals("before", sink.built.get("minecraft:sound_event|mymod:mutable"));
        assertEquals(1, first.registered().size());
        // The fingerprint makes no promise to cover the side effects of an arbitrary supplier. A bare
        // supplier records an "additional:" style origin marker.
        assertEquals("test", first.registered().get(0).origin());
    }

    /** Recording sink, the same one the end-to-end test uses. */
    static class RecordingSink implements StartupRegistryRuntime.RegistrySink {
        final List<String> registered = new ArrayList<>();
        final java.util.Map<String, Object> built = new java.util.LinkedHashMap<>();

        @Override
        public void register(
                net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> registry,
                Identifier id, java.util.function.Supplier<?> supplier) {
            built.put(registry.identifier() + "|" + id, supplier.get());
            registered.add(registry.identifier() + "|" + id);
        }
    }
}
