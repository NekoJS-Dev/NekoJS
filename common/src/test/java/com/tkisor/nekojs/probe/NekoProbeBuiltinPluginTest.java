package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugin registration of the built-in probe backends: NekoProbeBuiltinPlugin registers
 * ("typescript","builtin") and ("python","builtin") through the {@code nekojs:probe_backends}
 * extension point, and after bootstrap the global singleton must contain both and be locked.
 * The name "builtin" is a string contract with NekoJSCommands on every platform and must not drift.
 */
class NekoProbeBuiltinPluginTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetProbeBackendRegistry() throws Exception {
        Field inst = ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        inst.setAccessible(true);
        inst.set(null, null);
    }

    @Test
    void bootstrapRegistersBuiltinBackendsThroughExtensionPoint() {
        NekoPluginBootstrap.bootstrap(
                List.of(new NekoProbeBuiltinPlugin()), new ScriptPropertyRegistry.Impl());

        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        assertTrue(registry.isLocked(), "registry must be locked after bootstrap");
        assertTrue(registry.backend("typescript", "builtin").isPresent(),
                "(typescript, builtin) must be registered — platform commands look it up by name");
        assertTrue(registry.backend("python", "builtin").isPresent(),
                "(python, builtin) must be registered");
        assertTrue(registry.languages().containsAll(List.of("typescript", "python")));
    }

    @Test
    void bootstrapWithoutBuiltinPluginLeavesNoDefaultTypescriptBackend() {
        // Fallback semantics: the extension point no longer hardcodes registrations, so without the plugin
        // there is no backend (discovery must be able to find NekoProbeBuiltinPlugin)
        NekoPluginBootstrap.bootstrap(List.of(), new ScriptPropertyRegistry.Impl());

        assertFalse(ProbeBackendRegistry.get().backend("typescript", "builtin").isPresent());
        assertNotNull(ProbeBackendRegistry.get());
    }
}
