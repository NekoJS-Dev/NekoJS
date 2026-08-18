package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NekoPluginExtensionProvider} 冒烟测试：证明「第三方插件注册自定义扩展点」机制真实可用。
 *
 * <ul>
 *   <li>provider 注册的自定义扩展点在 bootstrap 中对全部插件按列表顺序逐个收集（含定义者自己）；</li>
 *   <li>自定义 id 之间重复、以及自定义 id 与内置 {@code nekojs:*} id 冲突时，
 *       bootstrap fail-fast 抛 {@link IllegalArgumentException}；</li>
 *   <li>collectExtensions 窗口关闭（freeze）后，registry 再接受注册抛 {@link IllegalStateException}。</li>
 * </ul>
 *
 * <p>harness 与 {@code NekoProbeBuiltinPluginTest} 相同：@BeforeAll 初始化测试 Platform；
 * 每次 bootstrap 结束都会 {@code ProbeBackendRegistry.setInstance}（全局单例、仅允许设置一次），
 * 故 @BeforeEach 反射清空 {@code ProbeBackendRegistry.INSTANCE} 保证各测试可独立跑 bootstrap。
 */
class NekoPluginExtensionProviderTest {

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
    void customExtensionPointCollectsEveryPluginIncludingItsDefiner() {
        RecordingProviderPlugin provider = new RecordingProviderPlugin();
        NekoJSPlugin plain = new PlainPlugin();

        NekoPluginBootstrap.bootstrap(List.of(provider, plain), new ScriptPropertyRegistry.Impl());

        assertEquals(List.of("RecordingProviderPlugin", "PlainPlugin"), provider.collected,
                "custom extension point must collect every plugin in list order, definer included");
    }

    @Test
    void duplicateCustomIdFailsBootstrap() {
        NekoPluginExtensionProvider first = registry -> registry.register(point("test:duplicate"));
        NekoPluginExtensionProvider second = registry -> registry.register(point("test:duplicate"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(first, second), new ScriptPropertyRegistry.Impl()));
        assertTrue(ex.getMessage().contains("test:duplicate"),
                "conflict message must name the offending id, got: " + ex.getMessage());
    }

    @Test
    void customIdCollidingWithBuiltInIdFailsBootstrap() {
        NekoPluginExtensionProvider intruder = registry -> registry.register(point("nekojs:bindings"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(intruder), new ScriptPropertyRegistry.Impl()));
        assertTrue(ex.getMessage().contains("nekojs:bindings"),
                "built-in ids are reserved, got: " + ex.getMessage());
    }

    @Test
    void registryIsFrozenAfterBootstrapAndRejectsLateRegistration() {
        AtomicReference<NekoPluginExtensionRegistry> captured = new AtomicReference<>();
        NekoPluginExtensionProvider provider = captured::set;

        NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl());

        NekoPluginExtensionRegistry registry = captured.get();
        assertThrows(IllegalStateException.class, () -> registry.register(point("test:late")),
                "registration window closes once collectExtensions freezes the registry");
    }

    private static NekoPluginExtensionPoint<NekoJSPlugin> point(String id) {
        return NekoPluginExtensionPoint.of(id, NekoJSPlugin.class, (plugin, context) -> {
        });
    }

    /** 同时实现 NekoJSPlugin + NekoPluginExtensionProvider：注册 "test:custom" 并记录被收集的插件类名。 */
    private static final class RecordingProviderPlugin implements NekoPluginExtensionProvider {
        final List<String> collected = new ArrayList<>();

        @Override
        public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
            registry.register(NekoPluginExtensionPoint.of(
                    "test:custom",
                    NekoJSPlugin.class,
                    (plugin, context) -> collected.add(plugin.getClass().getSimpleName())));
        }
    }

    /** 普通插件（不实现 provider）：验证自定义扩展点对非定义者插件同样收集。 */
    private static final class PlainPlugin implements NekoJSPlugin {
    }
}
