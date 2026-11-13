package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine semantics of the self-contained extension point shape (initializer / collector /
 * finisher / handle) and of point-first execution order:
 *
 * <ul>
 *   <li>Point-first order is observable: a point registered later sees an earlier point's product
 *       inside collect (while the second point collects any plugin, the first point has already
 *       finished and published);</li>
 *   <li>A finisher product can be retrieved through the registered handle
 *       {@link NekoPluginExtensionHandle} and through the result container
 *       ({@code NekoPluginRuntime.extensionProduct});</li>
 *   <li>Two consecutive bootstraps (a full reload) share no state and throw no NPE: each
 *       bootstrap rebuilds the accumulators, registrations from the first round do not appear in
 *       the second round's products, and the probe singleton is replaced instead of failing.</li>
 * </ul>
 */
class NekoPluginExtensionPointTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** A later point reads an earlier point's product in collect: the second point records what it sees. */
    private static final class SequencingProvider implements NekoPluginExtensionProvider {
        NekoPluginExtensionHandle<List<String>> firstHandle;

        @Override
        public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
            NekoPluginExtensionPoint<NekoJSPlugin, List<String>, List<String>> first =
                    NekoPluginExtensionPoint.<NekoJSPlugin, List<String>, List<String>>builder(
                            "test:first",
                            NekoJSPlugin.class)
                            .merge(MergePolicy.append())
                            .initializer(context -> new ArrayList<String>())
                            .collector((plugin, acc) -> acc.add("first:" + plugin.getClass().getSimpleName()))
                            .finish(acc -> List.copyOf(acc))
                            .build();
            firstHandle = registry.register(first);
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, SecondAccumulator, List<String>>builder("test:second", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new SecondAccumulator(context))
                    .collector((plugin, acc) -> acc.record("second:" + plugin.getClass().getSimpleName()
                            + ":firstProduct=" + acc.context().result("test:first", List.class)))
                    .finish(acc -> List.copyOf(acc.records))
                    .build());
        }
    }

    /** Accumulator of the second point: holds the bootstrap context and queries earlier products in collect. */
    private static final class SecondAccumulator {
        private final NekoPluginExtensionContext context;
        private final List<String> records = new ArrayList<>();

        SecondAccumulator(NekoPluginExtensionContext context) {
            this.context = context;
        }

        NekoPluginExtensionContext context() {
            return context;
        }

        void record(String entry) {
            records.add(entry);
        }
    }

    /** Plain plugin, collected like any other: a custom point also collects plugins that do not define it. */
    private static final class PlainPluginA implements NekoJSPlugin {
    }

    private static final class PlainPluginB implements NekoJSPlugin {
    }

    @Test
    void laterPointSeesEarlierPointProductInsideCollect() {
        SequencingProvider provider = new SequencingProvider();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider, new PlainPluginA(), new PlainPluginB()),
                new ScriptPropertyRegistry.Impl());

        // Point-first order: the first point finishes collecting all three plugins before the
        // second point starts; every second-point collect sees the complete first product
        assertEquals(List.of(
                "second:SequencingProvider:firstProduct=[first:SequencingProvider, first:PlainPluginA, first:PlainPluginB]",
                "second:PlainPluginA:firstProduct=[first:SequencingProvider, first:PlainPluginA, first:PlainPluginB]",
                "second:PlainPluginB:firstProduct=[first:SequencingProvider, first:PlainPluginA, first:PlainPluginB]"),
                runtime.extensionProduct("test:second", List.class),
                "collector of the later point must observe the finished product of the earlier point");
    }

    @Test
    void finisherProductRetrievableViaHandleAndResultContainer() {
        SequencingProvider provider = new SequencingProvider();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(provider, new PlainPluginA()), new ScriptPropertyRegistry.Impl());

        List<String> expected = List.of("first:SequencingProvider", "first:PlainPluginA");

        assertTrue(provider.firstHandle.isFinished(), "handle must be finished after bootstrap completes");
        assertEquals(expected, provider.firstHandle.result(),
                "handle must expose the finisher product of its extension point");
        assertEquals("test:first", provider.firstHandle.pointId());
        assertEquals(expected, runtime.extensionProduct("test:first", List.class),
                "result container must expose the same product");
        assertTrue(runtime.extensionProducts().containsKey("test:second"),
                "custom point products must appear in the runtime result container");
        assertNull(runtime.extensionProduct("test:missing", List.class),
                "unknown point id yields null instead of throwing");

        assertThrows(IllegalStateException.class,
                () -> runtime.extensionProduct("test:first", String.class),
                "product type mismatch must fail fast");
    }

    /** Two consecutive bootstraps (a full reload): no leaked state, no NPE. */
    private static final class NodeModulePlugin implements NekoJSPlugin, NodeModulesPoint.Contributor {
        @Override
        public void registerNodeModules(NodeModuleRegister registry) {
            registry.register("test:round", "module.exports = 'round'");
        }
    }

    @Test
    void consecutiveBootstrapsDoNotLeakStateOrCrash() {
        NekoPluginRuntime first = NekoPluginBootstrap.bootstrap(
                List.of(new NodeModulePlugin()), new ScriptPropertyRegistry.Impl());
        assertEquals("module.exports = 'round'", first.nodeModules().get("test:round"),
                "first round must see the plugin-registered module");

        // The second round clears no static singleton by reflection: the probe finisher's setInstance
        // must replace the old one
        NekoPluginRuntime second = assertDoesNotThrow(
                () -> NekoPluginBootstrap.bootstrap(List.of(), new ScriptPropertyRegistry.Impl()),
                "second bootstrap (simulated full reload) must not crash on static singletons");

        assertFalse(second.nodeModules().containsKey("test:round"),
                "accumulators are rebuilt per bootstrap: first round registrations must not leak");
        assertTrue(ProbeBackendRegistry.get().isLocked(),
                "probe singleton must be replaced (not duplicated) by the second bootstrap");

        // The second round registers the same module id again: the new accumulator accepts writes and
        // the product is delivered
        NekoPluginRuntime third = NekoPluginBootstrap.bootstrap(
                List.of(new NodeModulePlugin()), new ScriptPropertyRegistry.Impl());
        assertEquals("module.exports = 'round'", third.nodeModules().get("test:round"));
    }

    /**
     * Built-in points use the same shape: the finisher product goes straight into the result
     * container (the probe point is one example).
     */
    @Test
    void builtInPointsRunAsSelfContainedPoints() {
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(), new ScriptPropertyRegistry.Impl());

        assertSame(ProbeBackendRegistry.get(),
                runtime.extensionProduct("nekojs:probe_backends", ProbeBackendRegistry.class),
                "probe_backends finisher must publish the locked registry as its product");
        assertTrue(runtime.extensionProducts().keySet().containsAll(List.of(
                "nekojs:script_compilers", "nekojs:script_properties", "nekojs:bindings",
                "nekojs:adapters", "nekojs:type_docs", "nekojs:node_type_docs", "nekojs:node_modules",
                "nekojs:events", "nekojs:recipe_namespaces", "nekojs:recipe_schemas",
                "nekojs:recipe_lifecycle", "nekojs:lifecycle", "nekojs:probe_backends")),
                "all built-in points must publish products into the result container");
    }

    /**
     * registerEvents and registerClientEvents register groups with the same name: a server process
     * has only the server bus.
     */
    private static final class MixedEventsPlugin implements NekoJSPlugin, EventsPoint.Contributor, ClientEventsPoint.Contributor {
        @Override
        public void registerEvents(EventGroupRegistry registry) {
            EventGroup group = EventGroup.of("SharedEvents");
            group.server("serverBus", Object.class);
            registry.register(group);
        }

        @Override
        public void registerClientEvents(EventGroupRegistry registry) {
            EventGroup group = EventGroup.of("SharedEvents");
            group.client("clientBus", Object.class);
            registry.register(group);
        }
    }

    /**
     * client_events (collected on the client only) reads the {@code nekojs:events} product in its
     * initializer: on a client process the two same-named groups merge and the product freezes; on
     * a dedicated server the whole point is skipped and its product falls back to the events
     * product.
     */
    @Test
    void clientEventsPointMergesEarlierEventsProductOnClientOnly() throws Exception {
        // The default test platform has isClient=false: client_events is skipped and the group has only the server bus
        NekoPluginRuntime serverRuntime = NekoPluginBootstrap.bootstrap(
                List.of(new MixedEventsPlugin()), new ScriptPropertyRegistry.Impl());
        assertEquals(Set.of("serverBus"),
                serverRuntime.eventGroups().get("SharedEvents").viewBuses().keySet(),
                "on a dedicated server the client_events point must be skipped entirely");

        // Switch the platform to the client: the client_events initializer reads the events product and
        // merges same-named groups
        Field instance = Platform.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object original = instance.get(null);
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        IPlatform clientPlatform = (IPlatform) Proxy.newProxyInstance(
                IPlatform.class.getClassLoader(),
                new Class<?>[]{IPlatform.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isClient")) {
                        return Boolean.TRUE;
                    }
                    return method.invoke(new TestPlatformInit.TestIPlatform(gameDir), args);
                });
        try {
            instance.set(null, clientPlatform);
            NekoPluginRuntime clientRuntime = NekoPluginBootstrap.bootstrap(
                    List.of(new MixedEventsPlugin()), new ScriptPropertyRegistry.Impl());

            EventGroup merged = clientRuntime.eventGroups().get("SharedEvents");
            assertNotNull(merged, "client bootstrap must expose the merged group");
            assertEquals(Set.of("serverBus", "clientBus"), merged.viewBuses().keySet(),
                    "client_events must merge its buses into the earlier events product's group");
            assertThrows(IllegalStateException.class,
                    () -> merged.server("lateBus", Object.class),
                    "merged event groups must be frozen by the client_events finisher");
        } finally {
            instance.set(null, original);
        }
    }
}
