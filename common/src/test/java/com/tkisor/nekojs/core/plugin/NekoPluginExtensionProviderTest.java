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
 * Smoke test for {@link NekoPluginExtensionProvider}: the mechanism that lets a third-party
 * plugin register a custom extension point really works.
 *
 * <ul>
 *   <li>During bootstrap, the custom point registered by the provider collects every plugin in
 *       list order, including the one that defines it;</li>
 *   <li>When custom ids repeat or collide with a built-in {@code nekojs:*} id, bootstrap fails
 *       fast with {@link IllegalArgumentException};</li>
 *   <li>After the collectExtensions window closes (freeze), the registry throws
 *       {@link IllegalStateException} on further registration.</li>
 * </ul>
 *
 * <p>The harness matches {@code NekoProbeBuiltinPluginTest}: @BeforeAll initializes the test
 * Platform. The probe_backends finisher calls {@code ProbeBackendRegistry.setInstance} (a global
 * singleton that a reload replaces), so @BeforeEach clears {@code ProbeBackendRegistry.INSTANCE}
 * by reflection to let each test bootstrap on its own.
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

    private static NekoPluginExtensionPoint<NekoJSPlugin, ?, ?> point(String id) {
        return NekoPluginExtensionPoint.<NekoJSPlugin, NekoPluginExtensionContext, NekoPluginExtensionContext>builder(id, NekoJSPlugin.class)
                .merge(MergePolicy.append())
                .initializer(context -> context)
                .collector((plugin, context) -> {
                })
                .finish(context -> context)
                .build();
    }

    /**
     * Implements NekoJSPlugin and NekoPluginExtensionProvider: registers "test:custom" and records
     * the collected plugin class names.
     */
    private static final class RecordingProviderPlugin implements NekoPluginExtensionProvider {
        final List<String> collected = new ArrayList<>();
        public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
            registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, NekoPluginExtensionContext, NekoPluginExtensionContext>builder("test:custom", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> context)
                    .collector((plugin, context) -> collected.add(plugin.getClass().getSimpleName()))
                    .finish(context -> context)
                    .build());
        }
    }

    /** Plain plugin (not a provider): the custom point also collects plugins that do not define it. */
    private static final class PlainPlugin implements NekoJSPlugin {
    }
}
