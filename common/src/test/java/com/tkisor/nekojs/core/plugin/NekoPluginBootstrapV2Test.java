package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the extension point model on the real bootstrap engine:
 *
 * <ul>
 *   <li>Topological sort: a point registered before the point it names in {@code dependsOn} runs
 *       after that dependency (points of the same level without a dependency keep registration
 *       order);</li>
 *   <li>Cyclic dependencies fail fast: the error contains the complete cycle path;</li>
 *   <li>An unregistered dependency id fails early at freeze;</li>
 *   <li>A data dependency read out of order (target registered but not finished, no
 *       {@code dependsOn}) throws at the read site, and the error suggests "declare dependsOn";</li>
 *   <li>Two access tiers: {@code result} returns null for an unregistered point,
 *       {@code resultOrThrow} throws.</li>
 * </ul>
 */
class NekoPluginBootstrapV2Test {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** The dependent registers first and the dependency registers later: dependsOn must fix the order. */
    @Test
    void topoSortReordersRegisteredDependency() {
        NekoPluginExtensionProvider provider = registry -> {
            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> dependency =
                    NekoPluginExtensionPoint.<NekoJSPlugin, List<String>, List<String>>builder(
                            "test:dep", NekoJSPlugin.class)
                            .merge(MergePolicy.append())
                            .initializer(ctx -> new ArrayList<String>())
                            .collector((plugin, acc) -> acc.add("dep"))
                            .finish(List::copyOf)
                            .build();
            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> dependent =
                    NekoPluginExtensionPoint.<NekoJSPlugin, List<String>, List<String>>builder(
                            "test:dependent", NekoJSPlugin.class)
                            .merge(MergePolicy.append())
                            .dependsOn(dependency)
                            .initializer(ctx -> {
                                List<String> acc = new ArrayList<>();
                                acc.add("seen=" + ctx.result(dependency));
                                return acc;
                            })
                            .collector((plugin, acc) -> { })
                            .finish(List::copyOf)
                            .build();
            registry.register(dependent);
            registry.register(dependency);
        };

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("seen=[dep]"),
                runtime.extensionProduct("test:dependent", List.class));
    }

    @Test
    void dependencyCycleFailsFastWithPath() {
        NekoPluginExtensionProvider provider = registry -> {
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:a", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .dependsOnId("test:b")
                    .initializer(ctx -> new ArrayList<String>())
                    .collector((plugin, acc) -> { })
                    .finish(List::copyOf)
                    .build());
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:b", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .dependsOnId("test:a")
                    .initializer(ctx -> new ArrayList<String>())
                    .collector((plugin, acc) -> { })
                    .finish(List::copyOf)
                    .build());
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("cycle"), "报错应说明成环: " + error.getMessage());
        assertTrue(error.getMessage().contains("test:a") && error.getMessage().contains("test:b"),
                "报错应包含环路径: " + error.getMessage());
    }

    @Test
    void unknownDependencyIdFailsAtFreeze() {
        NekoPluginExtensionProvider provider = registry -> registry.register(
                NekoPluginExtensionPoint
                        .<NekoJSPlugin, List<String>, List<String>>builder("test:typo", NekoJSPlugin.class)
                        .merge(MergePolicy.append())
                        .dependsOnId("test:does-not-exist")
                        .initializer(ctx -> new ArrayList<String>())
                        .collector((plugin, acc) -> { })
                        .finish(List::copyOf)
                        .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("unregistered"), "报错应指向未注册 id: " + error.getMessage());
    }

    /** Reads a registered but unfinished point with no dependsOn: the read throws at once and points at the fix. */
    @Test
    void undeclaredDataDependencyThrowsAtReadSite() {
        NekoPluginExtensionProvider provider = registry -> {
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:reader", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    // Intentionally no dependsOn: reader registers and runs first, then reads the registered but unfinished late
                    .initializer(ctx -> {
                        ctx.resultOrThrow("test:late", List.class);
                        return new ArrayList<String>();
                    })
                    .collector((plugin, acc) -> { })
                    .finish(List::copyOf)
                    .build());
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:late", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(ctx -> new ArrayList<String>())
                    .collector((plugin, acc) -> { })
                    .finish(List::copyOf)
                    .build());
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("declare dependsOn") || error.getMessage().contains("has not finished"),
                "报错应附 dependsOn 修复指引: " + error.getMessage());
    }

    /** result() returns null for an unregistered point (optional tier); resultOrThrow() throws (required tier). */
    @Test
    void accessTiersDifferOnUnregisteredPoint() {
        List<String> observed = new ArrayList<>();
        NekoPluginExtensionProvider provider = registry -> registry.register(
                NekoPluginExtensionPoint
                        .<NekoJSPlugin, List<String>, List<String>>builder("test:probe", NekoJSPlugin.class)
                        .merge(MergePolicy.append())
                        .initializer(ctx -> {
                            observed.add("optional=" + ctx.result("test:missing", List.class));
                            try {
                                ctx.resultOrThrow("test:missing", List.class);
                            } catch (IllegalStateException expected) {
                                observed.add("required=threw");
                            }
                            return new ArrayList<>(observed);
                        })
                        .collector((plugin, acc) -> { })
                        .finish(List::copyOf)
                        .build());

        NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("optional=null", "required=threw"), observed);
    }

    // ---- Optional ordering dependencies (dependsOnOptionalId): read another point's product that may be absent ----

    /**
     * The optional dependency is absent: no edge and no early failure, and the point still runs
     * (the common-only bootstrap shape).
     */
    @Test
    void optionalDependencyAbsentDoesNotFailFreeze() {
        NekoPluginExtensionProvider provider = registry -> registry.register(
                NekoPluginExtensionPoint
                        .<NekoJSPlugin, List<String>, List<String>>builder("test:reader", NekoJSPlugin.class)
                        .merge(MergePolicy.append())
                        .dependsOnOptionalId("test:absent")
                        .initializer(ctx -> new ArrayList<String>())
                        .collector((plugin, acc) -> acc.add("ran"))
                        .finish(List::copyOf)
                        .build());

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("ran"), runtime.extensionProduct("test:reader", List.class));
    }

    /**
     * The optional dependency is present: the edge is added and orders execution, so a dependent
     * registered first is reordered after it.
     */
    @Test
    void optionalDependencyPresentOrdersExecution() {
        NekoPluginExtensionProvider provider = registry -> {
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:reader", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .dependsOnOptionalId("test:late")
                    .initializer(ctx -> {
                        List<String> acc = new ArrayList<>();
                        acc.add("seen=" + ctx.resultOrThrow("test:late", List.class));
                        return acc;
                    })
                    .collector((plugin, acc) -> { })
                    .finish(List::copyOf)
                    .build());
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:late", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(ctx -> new ArrayList<String>())
                    .collector((plugin, acc) -> acc.add("late"))
                    .finish(List::copyOf)
                    .build());
        };

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("seen=[late]"), runtime.extensionProduct("test:reader", List.class));
    }

    /**
     * Startup regression check: a version-tree plugin eagerly reads the
     * {@code nekojs:registry_types} product through a handle inside the doc hook, the
     * {@code requireResult} shape.
     *
     * <p>Negative control: the target doc point declares no dependency and runs before the types
     * point registers, so the read throws "has not finished yet". That failure shows the shape
     * really breaks, which is what makes the positive case meaningful.
     */
    @Test
    void lateProductReadWithoutDeclaredDependencyThrows() {
        LateReadPlugin plugin = new LateReadPlugin("test:docs");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(plugin), new ScriptPropertyRegistry.Impl()));
        assertTrue(error.getMessage().contains("has not finished yet"),
                "读取点应因违序立即抛错: " + error.getMessage());
    }

    /**
     * Positive case for the startup regression: the real {@link TypeDocsPoint} (built in,
     * registered first) declares the optional dependency, so the later registered
     * {@code nekojs:registry_types} is reordered to finish before it and the doc hook reads the
     * product in order. Without that declaration the same shape fails during the FML mod
     * constructor with "has not finished yet".
     */
    @Test
    void typeDocsPointIsOrderedAfterRegistryTypesWhenPresent() {
        LateReadPlugin plugin = new LateReadPlugin(TypeDocsPoint.ID);

        NekoPluginBootstrap.bootstrap(List.of(plugin), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("saw=[types]"), plugin.observed());
    }

    /**
     * Mimics the version-tree registration plugin: it registers {@code nekojs:registry_types}
     * (with a handle) and eagerly reads that handle's product inside the doc collection hook, the
     * same shape as {@code NekoRegistryPointsPlugin}. When {@code docPointId} is the real
     * {@link TypeDocsPoint#ID}, the read happens inside the real point's collection; otherwise the
     * plugin builds its own dependency-free doc point of the same shape as the negative control.
     */
    static final class LateReadPlugin
            implements NekoJSPlugin, NekoPluginExtensionProvider, TypeDocsPoint.Contributor {

        private final String docPointId;
        private final List<String> observed = new ArrayList<>();
        private NekoPluginExtensionHandle<?> typesHandle;

        LateReadPlugin(String docPointId) {
            this.docPointId = docPointId;
        }

        List<String> observed() {
            return List.copyOf(observed);
        }

        @Override
        public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
            if (!TypeDocsPoint.ID.equals(docPointId)) {
                registry.register(NekoPluginExtensionPoint
                        .<NekoJSPlugin, List<String>, List<String>>builder(docPointId, NekoJSPlugin.class)
                        .merge(MergePolicy.append())
                        .initializer(ctx -> new ArrayList<String>())
                        .collector((plugin, acc) -> ((LateReadPlugin) plugin).readTypes())
                        .finish(List::copyOf)
                        .build());
            }
            typesHandle = registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("nekojs:registry_types", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(ctx -> new ArrayList<String>())
                    .collector((plugin, acc) -> acc.add("types"))
                    .finish(List::copyOf)
                    .build());
        }

        @Override
        public void registerTypeDocs(TypeDocsRegister registry) {
            if (TypeDocsPoint.ID.equals(docPointId)) {
                readTypes();
            }
        }

        private void readTypes() {
            if (typesHandle == null || !typesHandle.isFinished()) {
                throw new IllegalStateException(
                        "extension point 'nekojs:registry_types' has not finished yet (bootstrap incomplete)");
            }
            observed.add("saw=" + typesHandle.result());
        }
    }
}
