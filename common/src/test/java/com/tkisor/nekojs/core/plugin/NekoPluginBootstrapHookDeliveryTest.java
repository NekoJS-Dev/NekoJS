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
 * Smoke tests for how bootstrap delivers extension points: {@code registerNodeModules},
 * {@code registerRecipeSchemas} and the lifecycle convenience hooks ({@code init},
 * {@code initStartup}, {@code afterInit}, {@code beforeScriptsLoaded},
 * {@code afterScriptsLoaded}).
 *
 * <p>The test drives a recording plugin through {@link NekoPluginBootstrap#bootstrap}, using the
 * same harness as {@code NekoCommonBuiltinPluginTest}, and checks that:
 * <ul>
 *   <li>modules registered by {@code registerNodeModules} appear in
 *       {@link NekoPluginRuntime#nodeModules()};</li>
 *   <li>schemas registered by {@code registerRecipeSchemas} are published to
 *       {@link RecipeTypeDefinitionStorage} by {@code publishRecipeSchemaOverrides} when the
 *       runtime is built (the test also checks the snapshot through the
 *       runtime.recipeSchemaOverrides() accessor);</li>
 *   <li>the default {@code registerLifecycleHooks} collects the five convenience methods as
 *       method references into the runtime. Bootstrap does not fire them early; each
 *       {@code fireXxx} call fires them in order and isolates exceptions.</li>
 * </ul>
 */
class NekoPluginBootstrapHookDeliveryTest {

    /** Schema for the smoke tests: namespace=smoke, type=machine. The test checks that the same
     * instance comes back from the storage layer. */
    private static final RecipeTypeDefinition SMOKE_SCHEMA = new RecipeTypeDefinition(
            "smoke", "machine", "smoke:machine", "smoke_machine",
            List.of(List.of("output")), Map.of(), List.of());

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetGlobals() throws Exception {
        // Bootstrap's collectExtensions calls ProbeBackendRegistry.setInstance, which throws when an
        // instance is already set, so clear it first.
        Field probeInstance = ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        probeInstance.setAccessible(true);
        probeInstance.set(null, null);

        // Clear the four static registries of RecipeTypeDefinitionStorage so leftovers from other tests
        // in the same JVM do not leak in; the assertions read the merged current() view, which a
        // leftover layer would disturb.
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

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(List.of(plugin), new ScriptPropertyRegistry.Impl());

        assertTrue(plugin.calls.contains("registerRecipeSchemas"),
                "nekojs:recipe_schemas 扩展点必须调用插件的 registerRecipeSchemas");
        assertSame(SMOKE_SCHEMA, runtime.recipeSchemaOverrides().get("smoke").get("machine"),
                "runtime 快照必须能直接取回插件注册的 schema（recipeSchemaOverrides() 访问器）");
        assertSame(SMOKE_SCHEMA, RecipeTypeDefinitionStorage.current().get("smoke", "machine"),
                "插件注册的 schema 必须经 publishRecipeSchemaOverrides 发布到 RecipeTypeDefinitionStorage");
    }

    @Test
    void lifecycleConvenienceHooksAreCollectedAtBootstrapAndFiredByRuntime() {
        RecordingPlugin plugin = new RecordingPlugin();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(plugin), new ScriptPropertyRegistry.Impl());

        // The collection phase only calls the registration hooks. The five lifecycle convenience
        // methods are collected as method references and do not fire during bootstrap.
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

    /** Recording plugin passed straight to bootstrap: it records extension point collection and
     * lifecycle firing, and the call order matters. */
    private static final class RecordingPlugin implements NekoJSPlugin, NodeModulesPoint.Contributor, RecipeSchemasPoint.Contributor, LifecyclePoint.Contributor, RecipeLifecyclePoint.Contributor {
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

    /** Throws from init and beforeScriptsLoaded to test how the fire entry points isolate
     * exceptions. */
    private static final class ThrowingLifecyclePlugin implements NekoJSPlugin, LifecyclePoint.Contributor {
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
