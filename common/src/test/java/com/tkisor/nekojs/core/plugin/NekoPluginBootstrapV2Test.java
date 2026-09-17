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
 * V2 扩展点模型语义（ADR-0001/0002）在真实 bootstrap 引擎上的验证：
 *
 * <ul>
 *   <li>拓扑排序：先注册但 {@code dependsOn} 后注册点的扩展点，执行被重排到依赖之后
 *       （同层无依赖时保持注册序）；</li>
 *   <li>环依赖 fail-fast：报错包含完整环路径；</li>
 *   <li>未注册依赖 id 在 freeze 早爆；</li>
 *   <li>数据依赖违序（已注册但未 finish、未声明 dependsOn）在读取点立即抛错，
 *       报错附"declare dependsOn"修复指引；</li>
 *   <li>两档访问：{@code result} 对未注册点返回 null，{@code resultOrThrow} 抛错。</li>
 * </ul>
 */
class NekoPluginBootstrapV2Test {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 依赖方先注册、被依赖方后注册：dependsOn 必须把执行序纠正过来。 */
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

    /** 读已注册但未 finish 的点（漏声明 dependsOn）：读取点立即抛错并附修复指引。 */
    @Test
    void undeclaredDataDependencyThrowsAtReadSite() {
        NekoPluginExtensionProvider provider = registry -> {
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, List<String>, List<String>>builder("test:reader", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    // 故意不声明 dependsOn：reader 先注册先执行，读到已注册但未 finish 的 late
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

    /** result() 对未注册点返回 null（可选依赖档），resultOrThrow() 抛错（必需依赖档）。 */
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

    // ---- 可选时序依赖（dependsOnOptionalId）：跨层"读对方产物、但对方可能缺席" ----

    /** 可选依赖对方缺席：不建边、不早爆，点照常执行（common-only bootstrap 的形态）。 */
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

    /** 可选依赖对方在场：建边且顺序生效（依赖方先注册也会被重排到对方之后）。 */
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
     * 启动期回归钉（ticket 15）：版本树插件在 doc 钩子里经句柄急切读
     * {@code nekojs:registry_types} 产物（{@code requireResult} 的等价形态）。
     *
     * <p>负例对照：目标 doc 点未声明依赖、先于 types 点注册执行 → 读取点抛
     * "has not finished yet"（证明该形状真的会炸，正例才有意义）。
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
     * 启动期回归钉（ticket 15）正例：真实 {@link TypeDocsPoint}（内置、先注册）声明可选依赖后，
     * 后注册的 {@code nekojs:registry_types} 被重排到它之前完成，doc 钩子里的产物读取不再违序。
     * 修复前该形状在游戏 bootstrap 里崩溃（FML mod 构造期 "has not finished yet"）。
     */
    @Test
    void typeDocsPointIsOrderedAfterRegistryTypesWhenPresent() {
        LateReadPlugin plugin = new LateReadPlugin(TypeDocsPoint.ID);

        NekoPluginBootstrap.bootstrap(List.of(plugin), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of("saw=[types]"), plugin.observed());
    }

    /**
     * 模拟版本树注册插件的形状：注册 {@code nekojs:registry_types}（含句柄），
     * 在 doc 收集钩子里急切读该句柄产物——与 {@code NekoRegistryPointsPlugin} 同款。
     * {@code docPointId} 为真实 {@link TypeDocsPoint#ID} 时读取发生在真实点的收集里；
     * 否则本插件自建同形状的无依赖 doc 点充当负例对照。
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
