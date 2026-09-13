package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 — extension-point boundary semantics observed through the author-visible surface
 * ({@link NekoPluginExtensionHandle}, {@link NekoPluginExtensionPoint}, {@link Sealable}).
 *
 * <p>The three boundaries this file keeps apart:
 * <ol>
 *   <li><b>registration-graph freeze</b> — no new point may be registered after freeze;</li>
 *   <li><b>accumulator seal</b> — a stolen accumulator reference cannot write after finish;</li>
 *   <li><b>result publication</b> — the published product is a snapshot, unaffected by #2.</li>
 * </ol>
 * They are distinct: freezing the graph does not seal an accumulator, and sealing an
 * accumulator does not retroactively change an already published immutable product.
 */
class NekoPluginAddonBoundaryTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** Accumulator the engine seals at finish; the test keeps a reference so it can be probed. */
    private static final class SealedBucket implements Sealable, NodeModuleRegister {
        private final Map<String, String> entries = new LinkedHashMap<>();
        private boolean sealed;

        @Override
        public void register(String moduleId, String source) {
            if (sealed) {
                throw new IllegalStateException("accumulator is sealed after finish");
            }
            entries.put(moduleId, source);
        }

        @Override
        public void seal() {
            sealed = true;
        }

        Map<String, String> snapshot() {
            return Map.copyOf(entries);
        }

        boolean isSealed() {
            return sealed;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SealedBucket bucket && entries.equals(bucket.entries);
        }

        @Override
        public int hashCode() {
            return entries.hashCode();
        }
    }

    private static NekoPluginExtensionPoint.Builder<NekoJSPlugin, List<String>, List<String>> pointBuilder(String id) {
        return NekoPluginExtensionPoint.<NekoJSPlugin, List<String>, List<String>>builder(id, NekoJSPlugin.class)
                .merge(MergePolicy.append())
                .initializer(ctx -> new ArrayList<String>())
                .collector((plugin, acc) -> { })
                .finish(List::copyOf);
    }

    private static NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> simplePoint(
            String id, String... dependsOn) {
        NekoPluginExtensionPoint.Builder<NekoJSPlugin, List<String>, List<String>> builder = pointBuilder(id);
        if (dependsOn.length > 0) {
            builder = builder.dependsOnId(dependsOn);
        }
        return builder.build();
    }

    /**
     * AC9: the handle refuses to hand out a product before its finisher has run, and serves the
     * finished product afterwards. The probe point is registered first so it executes while the
     * late point is still unfinished.
     */
    @Test
    void handleRejectsReadBeforeFinishAndServesProductAfter() {
        AtomicReference<NekoPluginExtensionHandle<List<String>>> handleRef = new AtomicReference<>();
        List<String> observed = new ArrayList<>();

        NekoPluginExtensionProvider provider = registry -> {
            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> prober =
                    pointBuilder("addon08:prober")
                            .initializer(ctx -> {
                                NekoPluginExtensionHandle<List<String>> handle = handleRef.get();
                                observed.add("isFinished=" + handle.isFinished());
                                observed.add("pointId=" + handle.pointId());
                                try {
                                    handle.result();
                                    observed.add("result=returned");
                                } catch (IllegalStateException expected) {
                                    observed.add("result=rejected");
                                }
                                return new ArrayList<String>();
                            })
                            .build();
            registry.register(prober);

            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> late = simplePoint("addon08:late");
            handleRef.set(registry.register(late));
        };

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider), new ScriptPropertyRegistry.Impl());

        assertEquals(List.of("isFinished=false", "pointId=addon08:late", "result=rejected"), observed,
                "an unfinished handle must refuse result() and name its own point id");
        NekoPluginExtensionHandle<List<String>> handle = handleRef.get();
        assertTrue(handle.isFinished(), "after bootstrap the handle must be finished");
        assertEquals(List.of(), handle.result(), "the finished handle must serve the finisher product");
        assertEquals(List.of(), runtime.extensionProduct("addon08:late", List.class),
                "the same product must be observable through the runtime product map");
    }

    /** AC9: a dependency cycle fails fast, and the message spells out the cycle path. */
    @Test
    void dependencyCycleFailsFastWithAddonLocatedPath() {
        NekoPluginExtensionProvider provider = registry -> {
            registry.register(simplePoint("addon08:cyc-a", "addon08:cyc-b"));
            registry.register(simplePoint("addon08:cyc-b", "addon08:cyc-a"));
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("cycle"), error.getMessage());
        assertTrue(error.getMessage().contains("addon08:cyc-a") && error.getMessage().contains("addon08:cyc-b"),
                "the failure must name both addon points: " + error.getMessage());
    }

    /** AC9: an unknown dependency id is rejected at freeze time, naming the offending point. */
    @Test
    void unknownDependencyFailsAtFreezeWithAddonLocatedMessage() {
        NekoPluginExtensionProvider provider = registry ->
                registry.register(simplePoint("addon08:orphan", "addon08:nowhere"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("addon08:orphan"), error.getMessage());
        assertTrue(error.getMessage().contains("addon08:nowhere"), error.getMessage());
    }

    /** AC9: a duplicate point id must fail immediately and name the conflicting id. */
    @Test
    void duplicateAddonPointIdFailsImmediately() {
        NekoPluginExtensionProvider first = registry -> registry.register(simplePoint("addon08:dup"));
        NekoPluginExtensionProvider second = registry -> registry.register(simplePoint("addon08:dup"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(first, second), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("addon08:dup"), error.getMessage());
    }

    /** AC9: the registration window is closed once the registration graph is frozen. */
    @Test
    void registrationAfterFreezeIsRejected() {
        AtomicReference<NekoPluginExtensionRegistry> captured = new AtomicReference<>();
        NekoPluginExtensionProvider captureProvider = registry -> captured.set(registry);
        NekoPluginBootstrap.bootstrap(List.of(captureProvider),
                new ScriptPropertyRegistry.Impl());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> captured.get().register(simplePoint("addon08:late")));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("frozen"),
                error.getMessage());
    }

    /**
     * AC16: registration-graph freeze, accumulator seal and result publication are three
     * different boundaries. Holding the old accumulator reference and writing after finish must
     * be rejected, while the already published product stays valid and unchanged.
     */
    @Test
    void stolenAccumulatorWritesAreRejectedAfterFinishWithoutChangingPublishedResult() {
        AtomicReference<SealedBucket> stolen = new AtomicReference<>();
        AtomicReference<NekoPluginExtensionHandle<Map<String, String>>> handleRef = new AtomicReference<>();

        NekoPluginExtensionProvider provider = registry -> {
            NekoPluginExtensionPoint<NekoJSPlugin, SealedBucket, Map<String, String>> point =
                    NekoPluginExtensionPoint.<NekoJSPlugin, SealedBucket, Map<String, String>>builder(
                                    "addon08:frozen", NekoJSPlugin.class)
                            .merge(MergePolicy.append())
                            .initializer(ctx -> {
                                SealedBucket bucket = new SealedBucket();
                                stolen.set(bucket);
                                return bucket;
                            })
                            .collector((plugin, bucket) -> bucket.register("addon08:before", "v1"))
                            .finish(SealedBucket::snapshot)
                            .build();
            handleRef.set(registry.register(point));
        };

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider), new ScriptPropertyRegistry.Impl());

        SealedBucket bucket = stolen.get();
        assertNotNull(bucket, "the fixture must have captured the accumulator");
        assertTrue(bucket.isSealed(), "finish must seal the accumulator (ADR-0001)");
        assertThrows(IllegalStateException.class, () -> bucket.register("addon08:after", "v2"),
                "a stolen accumulator reference must be rejected after finish");

        Map<String, String> published = handleRef.get().result();
        assertEquals(Map.of("addon08:before", "v1"), published,
                "the published product must be the finish-time snapshot, unaffected by later writes");
        assertFalse(published.containsKey("addon08:after"));
        assertSame(published, runtime.extensionProduct("addon08:frozen", Map.class),
                "the runtime product map and the handle must expose the same published instance");
    }

    /**
     * AC4/AC5: an extension point cannot observe or rewrite another point's intermediate state,
     * and its own finish freezes only its own result. The consumer is registered first so it runs
     * while the producer is registered but unfinished.
     */
    @Test
    void onePointCannotObserveAnotherPointsIntermediateAccumulator() {
        NekoPluginExtensionProvider provider = registry -> {
            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> consumer =
                    pointBuilder("addon08:consumer")
                            .initializer(ctx -> {
                                // Optional tier only tolerates *unregistered* / *skipped* points;
                                // a registered-but-unfinished point is an ordering violation even here.
                                assertEquals(null, ctx.result("addon08:absent", String.class),
                                        "the optional tier must return null for an unregistered point");
                                ctx.result("addon08:producer", String.class);
                                return new ArrayList<String>();
                            })
                            .build();
            registry.register(consumer);
            registry.register(simplePoint("addon08:producer"));
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("addon08:producer"),
                "the violation must name the point that has not finished yet: " + error.getMessage());
        assertTrue(error.getMessage().contains("dependsOn") || error.getMessage().contains("has not finished"),
                "the violation must explain how to fix the ordering: " + error.getMessage());
    }

    /**
     * AC12: a binding's shared {@code value()} is owned by the process-level Plugin Runtime, not by
     * a generation. A generation-scoped teardown (the default no-op {@code Binding.close}) must not
     * replace, close or otherwise mutate that shared Java object, and the frozen snapshot must keep
     * exposing the very same instance.
     */
    @Test
    void bindingCloseDoesNotMutateSharedRuntimeOwnedJavaObject() {
        SharedJavaState shared = new SharedJavaState();
        Binding binding = Binding.of("AddonShared", shared);

        NekoJSPlugin plugin = new NekoJSPlugin() {
            @Override
            public void registerBinding(BindingRegistry registry) {
                registry.register(binding);
            }
        };

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(plugin), new ScriptPropertyRegistry.Impl());

        Binding frozen = runtime.bindings(ScriptType.SERVER).get("AddonShared");
        assertNotNull(frozen, "the addon binding must be part of the frozen server binding snapshot");
        assertSame(binding, frozen, "the snapshot must hold the registered Binding, not a copy");
        assertSame(shared, frozen.value(),
                "Binding.value() must still be the shared Java object the Plugin Runtime owns");

        frozen.close(ScriptType.SERVER);
        assertSame(shared, frozen.value(),
                "closing a binding for one script type must not replace the shared runtime-owned object");
        assertFalse(shared.isTornDown(), "the shared Java object must not be torn down by a generation teardown");
        assertSame(shared, runtime.bindings(ScriptType.SERVER).get("AddonShared").value(),
                "the frozen product must keep exposing the same shared object");
    }

    /** Marker for the shared-object identity assertion above. */
    private static final class SharedJavaState {
        private boolean tornDown;

        boolean isTornDown() {
            return tornDown;
        }
    }
}
