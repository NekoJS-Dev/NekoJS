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
 * 扩展点自包含新形态（initializer / collector / finisher / handle）与点优先执行序的引擎语义：
 *
 * <ul>
 *   <li>点优先顺序可观测：后注册点在 collect 里能拿到先注册点的产物
 *       （第二个点收集任意插件时，第一个点已经 finish 并发布）；</li>
 *   <li>finisher 产物可经注册句柄 {@link NekoPluginExtensionHandle} 与结果容器
 *       （{@code NekoPluginRuntime.extensionProduct}）取回；</li>
 *   <li>连续两轮 bootstrap（模拟完整 reload）不串状态、不 NPE：
 *       扩展点每轮重建累积器，第一轮的注册内容不出现在第二轮产物中，
 *       probe 单例整体替换而非抛异常。</li>
 * </ul>
 */
class NekoPluginExtensionPointTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 后注册点在 collect 里引用先注册点的产物：第二个点记录 collect 时可见的 first 产物。 */
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

    /** 第二个点的累积器：持有 bootstrap context，collect 期可查询先注册点的产物。 */
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

    /** 普通插件：只作为被收集对象，验证自定义点对非定义者插件同样收集。 */
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

        // 点优先执行序：first 点先完整收集（三个插件）并 finish，
        // second 点才开始收集；second 的每次 collect 都能看到完整的 first 产物
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

    /** 连续两轮 bootstrap（模拟完整 reload）：不串状态、不 NPE。 */
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

        // 第二轮不反射清任何静态单例：probe finisher 的 setInstance 必须整体替换旧单例
        NekoPluginRuntime second = assertDoesNotThrow(
                () -> NekoPluginBootstrap.bootstrap(List.of(), new ScriptPropertyRegistry.Impl()),
                "second bootstrap (simulated full reload) must not crash on static singletons");

        assertFalse(second.nodeModules().containsKey("test:round"),
                "accumulators are rebuilt per bootstrap: first round registrations must not leak");
        assertTrue(ProbeBackendRegistry.get().isLocked(),
                "probe singleton must be replaced (not duplicated) by the second bootstrap");

        // 第二轮再注册同一模块 id：新累积器可写、产物正常交付
        NekoPluginRuntime third = NekoPluginBootstrap.bootstrap(
                List.of(new NodeModulePlugin()), new ScriptPropertyRegistry.Impl());
        assertEquals("module.exports = 'round'", third.nodeModules().get("test:round"));
    }

    /** 内置点也已迁移到新形态：finisher 产物直接进入结果容器（以 probe 点为例）。 */
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

    /** registerEvents 与 registerClientEvents 注册同名组：服务器进程只有 server 总线。 */
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
     * client_events（仅客户端收集）在 initializer 里引用 {@code nekojs:events} 的产物：
     * 客户端进程上两组同名合并、产物冻结；专用服务器进程整个点跳过，产物回退为 events 产物。
     */
    @Test
    void clientEventsPointMergesEarlierEventsProductOnClientOnly() throws Exception {
        // 默认测试平台 isClient=false：client_events 跳过，事件组只有 server 总线
        NekoPluginRuntime serverRuntime = NekoPluginBootstrap.bootstrap(
                List.of(new MixedEventsPlugin()), new ScriptPropertyRegistry.Impl());
        assertEquals(Set.of("serverBus"),
                serverRuntime.eventGroups().get("SharedEvents").viewBuses().keySet(),
                "on a dedicated server the client_events point must be skipped entirely");

        // 临时把平台换成客户端：client_events 的 initializer 读取 events 产物并合并同名组
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
