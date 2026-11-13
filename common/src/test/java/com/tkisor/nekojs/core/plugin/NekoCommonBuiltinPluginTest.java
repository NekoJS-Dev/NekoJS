package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NekoCommonBuiltinPlugin holds the common-only registrations that the three platform
 * NekoJSCorePlugins used to duplicate word for word: the TS/JSX language plugins, the four
 * built-in ScriptProperties, and the NodeModuleTypeDocs built-in declarations. The platform core
 * plugins no longer register them, and this test shows that the common plugin alone puts them
 * in place.
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
        assertNotNull(runtime.scriptCompilers().getLanguage(".jsx"), "JSX language must be registered");
        assertNotNull(runtime.scriptCompilers().getLanguage(".tsx"), "TSX language must be registered");
        assertEquals("typescript", runtime.scriptCompilers().getLanguage(".ts").id());
        assertEquals("jsx", runtime.scriptCompilers().getLanguage(".jsx").id());
        assertEquals("tsx", runtime.scriptCompilers().getLanguage(".tsx").id(),
                "tsx keeps its own language identity instead of collapsing into jsx");

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
