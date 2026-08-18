package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NekoCommonBuiltinPlugin 吸收三份平台 NekoJSCorePlugin 逐字重复的纯 common 注册
 * （TS/JSX 语言插件、四个内置 ScriptProperty、NodeModuleTypeDocs 内置声明）。
 * 平台 core plugin 不再自带这些注册——本测试保证仅凭 common 插件即可就位。
 */
class NekoCommonBuiltinPluginTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetProbeBackendRegistry() throws Exception {
        Field inst = com.tkisor.nekojs.probe.ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        inst.setAccessible(true);
        inst.set(null, null);
    }

    @Test
    void commonBuiltinRegistersLanguagesPropertiesAndNodeTypeDocs() {
        ScriptPropertyRegistry.Impl scriptProps = new ScriptPropertyRegistry.Impl();
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(new NekoCommonBuiltinPlugin()), scriptProps);

        assertNotNull(runtime.scriptCompilers().getLanguage(".ts"), "TypeScript language must be registered");
        assertNotNull(runtime.scriptCompilers().getLanguage(".tsx"), "JSX language must be registered");

        for (ScriptProperty<?> prop : List.of(ScriptProperty.AFTER, ScriptProperty.MODLOADED,
                ScriptProperty.DISABLE, ScriptProperty.PRIORITY)) {
            assertTrue(scriptProps.view().containsKey(prop.name),
                    "script property " + prop.name + " must be registered");
        }

        assertTrue(runtime.manualDeclarations().stream()
                        .anyMatch(d -> d.declaration() != null && d.declaration().contains("declare module")),
                "built-in node module declarations must be present");
    }
}
