package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bootstrap 扩展点交付冒烟：{@code registerNodeModules} / {@code registerRecipeSchemas}
 * 与生命周期便捷钩子（{@code init} / {@code initStartup} / {@code afterInit} /
 * {@code beforeScriptsLoaded} / {@code afterScriptsLoaded}）此前没有任何内置插件实现。
 *
 * <p>本测试用录制插件走 {@link NekoPluginBootstrap#bootstrap}（与
 * {@code NekoCommonBuiltinPluginTest} 相同的 harness），断言：
 * <ul>
 *   <li>{@code registerNodeModules} 注册的模块出现在 {@link NekoPluginRuntime#nodeModules()}；</li>
 *   <li>{@code registerRecipeSchemas} 注册的 schema 经 runtime 构造时的
 *       {@code publishRecipeSchemaOverrides} 发布到 {@link RecipeTypeDefinitionStorage}
 *       （runtime 本身无 recipeSchemaOverrides 访问器，存储层是唯一可观测面）；</li>
 *   <li>{@code registerLifecycleHooks} 默认实现把五个便捷方法以方法引用收集进 runtime，
 *       bootstrap 阶段不提前触发，{@code fireXxx} 时按序触发且异常被隔离。</li>
 * </ul>
 */
class NekoPluginBootstrapHookDeliveryTest {

    /** 冒烟用 schema：namespace=smoke、type=machine，断言同一实例从存储层取回。 */
    private static final RecipeTypeDefinition SMOKE_SCHEMA = new RecipeTypeDefinition(
            "smoke", "machine", "smoke:machine", "smoke_machine",
            List.of(List.of("output")), Map.of(), List.of());

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetGlobals() throws Exception {
        // bootstrap 的 collectExtensions 会 ProbeBackendRegistry.setInstance（已设置时抛异常），先清
        Field probeInstance = ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        probeInstance.setAccessible(true);
        probeInstance.set(null, null);

        // RecipeTypeDefinitionStorage 四层静态注册表清空，隔离同 JVM 其他测试的残留
        // （断言走 current() 合并视图，残留层会干扰取回结果）
        for (String layer : List.of("autoDiscovered", "pluginOverrides", "dataDriven", "scriptSchemas")) {
            storageField(layer).set(null, RecipeTypeDefinitionRegistry.EMPTY);
        }
    }

    @AfterEach
    void resetStorageLayers() throws Exception {
        for (String layer : List.of("autoDiscovered", "pluginOverrides", "dataDriven", "scriptSchemas")) {
            storageField(layer).set(null, RecipeTypeDefinitionRegistry.EMPTY);
        }
    }

    private static Field storageField(String name) throws NoSuchFieldException {
        Field f = RecipeTypeDefinitionStorage.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Test
    void registerNodeModulesDeliversModuleToRuntime() {
        RecordingPlugin plugin = new RecordingPlugin();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(plugin), new ScriptPropertyRegistry.Impl());

        assertTrue(plugin.calls.contains("registerNodeModules"),
                "nekojs:node_modules 扩展点必须调用插件的 registerNodeModules");
        assertEquals("module.exports = 'hello'", runtime.nodeModules().get("smoke:hello"),
                "插件注册的 node 模块必须出现在 runtime.nodeModules()（供 require('smoke:hello') 解析）");
    }

    @Test
    void registerRecipeSchemasPublishesOverrideIntoDefinitionStorage() {
        RecordingPlugin plugin = new RecordingPlugin();

        NekoPluginBootstrap.bootstrap(List.of(plugin), new ScriptPropertyRegistry.Impl());

        assertTrue(plugin.calls.contains("registerRecipeSchemas"),
                "nekojs:recipe_schemas 扩展点必须调用插件的 registerRecipeSchemas");
        assertSame(SMOKE_SCHEMA, RecipeTypeDefinitionStorage.current().get("smoke", "machine"),
                "插件注册的 schema 必须经 publishRecipeSchemaOverrides 发布到 RecipeTypeDefinitionStorage");
    }

    @Test
    void lifecycleConvenienceHooksAreCollectedAtBootstrapAndFiredByRuntime() {
        RecordingPlugin plugin = new RecordingPlugin();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(plugin), new ScriptPropertyRegistry.Impl());

        // 收集阶段只调用注册型钩子；五个生命周期便捷方法以方法引用收集，不在 bootstrap 时提前触发
        assertEquals(List.of("registerNodeModules", "registerRecipeSchemas"), plugin.calls,
                "bootstrap 阶段不应触发生命周期便捷方法");

        runtime.fireInit();
        runtime.fireInitStartup();
        runtime.fireAfterInit();
        runtime.fireBeforeScriptsLoaded(ScriptType.SERVER);
        runtime.fireAfterScriptsLoaded(ScriptType.CLIENT);

        assertEquals(List.of(
                "registerNodeModules",
                "registerRecipeSchemas",
                "init",
                "initStartup",
                "afterInit",
                "beforeScriptsLoaded:server",
                "afterScriptsLoaded:client"), plugin.calls,
                "五个生命周期 fire 入口必须按序调用 registerLifecycleHooks 默认实现收集的便捷方法");
    }

    @Test
    void failingLifecycleHookIsIsolatedAndDoesNotBlockLaterPlugins() {
        RecordingPlugin recording = new RecordingPlugin();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(new ThrowingLifecyclePlugin(), recording), new ScriptPropertyRegistry.Impl());

        assertDoesNotThrow(runtime::fireInit, "单个插件生命周期钩子异常应被隔离");
        assertDoesNotThrow(() -> runtime.fireBeforeScriptsLoaded(ScriptType.STARTUP),
                "单个插件生命周期钩子异常应被隔离");
        assertTrue(recording.calls.contains("init"), "前序插件 init 抛异常不应中断后续插件");
        assertTrue(recording.calls.contains("beforeScriptsLoaded:startup"),
                "前序插件 beforeScriptsLoaded 抛异常不应中断后续插件");
    }

    /** 直接实例化传入 bootstrap 的录制插件：记录扩展点收集与生命周期触发（调用顺序敏感）。 */
    private static final class RecordingPlugin implements NekoJSPlugin {
        final List<String> calls = new ArrayList<>();

        @Override
        public void registerNodeModules(NodeModuleRegister registry) {
            calls.add("registerNodeModules");
            registry.register("smoke:hello", "module.exports = 'hello'");
        }

        @Override
        public void registerRecipeSchemas(RecipeSchemaRegister registry) {
            calls.add("registerRecipeSchemas");
            registry.register("smoke", "machine", SMOKE_SCHEMA);
        }

        @Override
        public void init() {
            calls.add("init");
        }

        @Override
        public void initStartup() {
            calls.add("initStartup");
        }

        @Override
        public void afterInit() {
            calls.add("afterInit");
        }

        @Override
        public void beforeScriptsLoaded(ScriptType type) {
            calls.add("beforeScriptsLoaded:" + type.name);
        }

        @Override
        public void afterScriptsLoaded(ScriptType type) {
            calls.add("afterScriptsLoaded:" + type.name);
        }
    }

    /** init / beforeScriptsLoaded 抛异常，验证 fire 入口的异常隔离语义。 */
    private static final class ThrowingLifecyclePlugin implements NekoJSPlugin {
        @Override
        public void init() {
            throw new IllegalStateException("boom-init");
        }

        @Override
        public void beforeScriptsLoaded(ScriptType type) {
            throw new IllegalStateException("boom-beforeScriptsLoaded");
        }
    }
}
