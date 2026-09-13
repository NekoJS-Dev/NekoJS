package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
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
 * 内置 probe backend 的插件化注册：NekoProbeBuiltinPlugin 经
 * {@code nekojs:probe_backends} 扩展点注册 ("typescript","builtin") 与
 * ("python","builtin")，bootstrap 后全局单例必须包含两者且已 lock。
 * "builtin" 名字是各平台 NekoJSCommands 的字符串契约，不可漂移。
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
        // production owning entry (NekoPluginBootstrap.bootstrap is package-private test harness now)
        NekoPluginRuntime.bootstrapOwned(
                List.of(owned(new NekoProbeBuiltinPlugin())),
                new ScriptPropertyRegistry.Impl());

        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        assertTrue(registry.isLocked(), "registry must be locked after bootstrap");
        assertTrue(registry.backend("typescript", "builtin").isPresent(),
                "(typescript, builtin) must be registered — platform commands look it up by name");
        assertTrue(registry.backend("python", "builtin").isPresent(),
                "(python, builtin) must be registered");
        assertTrue(registry.languages().containsAll(List.of("typescript", "python")));
    }

    private static OwnedPlugin owned(com.tkisor.nekojs.api.NekoJSPlugin plugin) {
        return new OwnedPlugin(new PluginIdentity("test:" + plugin.getClass().getName(), plugin.getClass().getName(),
                java.net.URI.create("test:" + plugin.getClass().getName())), plugin);
    }

    @Test
    void bootstrapWithoutBuiltinPluginLeavesNoDefaultTypescriptBackend() {
        // 兜底语义：扩展点不再硬编码注册，没有插件就没有 backend（发现机制必须能找到 NekoProbeBuiltinPlugin）
        NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl());

        assertFalse(ProbeBackendRegistry.get().backend("typescript", "builtin").isPresent());
        assertNotNull(ProbeBackendRegistry.get());
    }
}
