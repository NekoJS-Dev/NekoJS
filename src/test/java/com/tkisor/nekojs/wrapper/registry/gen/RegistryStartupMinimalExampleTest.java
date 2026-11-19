package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the smallest working example of startup registration end to end through a real
 * GraalJS evaluation, following the production sequence: a STARTUP script installs its
 * listener, collection runs exactly once before the first registry pass, and each
 * registry is drained into the node sink.
 *
 * <p>The example covers default and named builders, a deferred supplier, and Bean property and
 * setter parity. It does not use Minecraft's native registration passes.
 */
class RegistryStartupMinimalExampleTest {

    private static final String NODE = "example-node";

    private static final String STARTUP_EXAMPLE = """
            StartupEvents.registry('sound_event', event => {
                event.create('mymod:boom', build => { build.fixedRange = 16 });
                event.create('mymod:ping', 'basic', build => { build.setFixedRange(32) });
            });
            StartupEvents.registry('villager_type', event => {
                event.create('mymod:scholar_variant', 'art', build => {});
                event.createCustom('mymod:scholar', () => 'raw-scholar');
            });
            StartupEvents.registry('item', event => {
                event.create('mymod:via_property', build => { build.maxStackSize = 16; build.rarity = 'EPIC'; });
                event.create('mymod:via_setter', build => { build.setMaxStackSize(16); build.setRarity('epic'); });
            });
            """;

    /**
     * A sink with two shapes. Registries in the {@code deferred} set follow the NeoForge
     * {@code RegisterEvent} shape: they only record the Supplier, which the platform runs
     * before registration freezes, because bare JUnit has no FML and building an ItemBuilder
     * needs the vanilla bootstrap. Every other registry runs its supplier immediately, the
     * same shape as a Fabric direct registration. Both shapes consume the same
     * {@code validatedSupplier} wrapped by the Runtime.
     */
    static final class RecordingSink implements StartupRegistryRuntime.RegistrySink {
        final List<String> registered = new ArrayList<>();
        final Map<String, Object> built = new LinkedHashMap<>();
        final java.util.Set<ResourceKey<? extends Registry<?>>> deferred;

        RecordingSink(java.util.Set<ResourceKey<? extends Registry<?>>> deferred) {
            this.deferred = deferred;
        }

        @Override
        public void register(ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
            if (deferred.contains(registry)) {
                registered.add(registry.identifier() + "|" + id);
                return;
            }
            built.put(registry.identifier() + "|" + id, supplier.get());
            registered.add(registry.identifier() + "|" + id);
        }
    }

    private static final class ExampleTypes implements RegistryTypesPoint.Contributor {
        @Override
        public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
            collector.registerType(Registries.VILLAGER_TYPE, "art", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        }
    }

    private static StartupRegistryRuntime runExample() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (var script = new StartupRegistryScriptFixture(new NekoRegistryPointsPlugin(), new ExampleTypes())) {
            script.context.eval("js", STARTUP_EXAMPLE);
            runtime.collectOnce();
            RecordingSink sink = new RecordingSink(java.util.Set.of(Registries.ITEM, Registries.VILLAGER_TYPE));
            StartupRegistryRuntime.DrainResult items = runtime.drainFor(Registries.ITEM, sink);
            StartupRegistryRuntime.DrainResult sounds = runtime.drainFor(Registries.SOUND_EVENT, sink);
            StartupRegistryRuntime.DrainResult villagers = runtime.drainFor(Registries.VILLAGER_TYPE, sink);
            verify(items, sounds, villagers, sink);
        }
        return runtime;
    }

    private static void verify(
            StartupRegistryRuntime.DrainResult items,
            StartupRegistryRuntime.DrainResult sounds,
            StartupRegistryRuntime.DrainResult villagers,
            RecordingSink sink) {
        assertEquals(List.of("minecraft:item|mymod:via_property", "minecraft:item|mymod:via_setter"),
                items.registered().stream().map(r -> r.registry().identifier() + "|" + r.definition()).toList());
        assertEquals(List.of("minecraft:sound_event|mymod:boom", "minecraft:sound_event|mymod:ping"),
                sounds.registered().stream().map(r -> r.registry().identifier() + "|" + r.definition()).toList());
        assertEquals(List.of("minecraft:villager_type|mymod:scholar_variant", "minecraft:villager_type|mymod:scholar"),
                villagers.registered().stream().map(r -> r.registry().identifier() + "|" + r.definition()).toList());

        ItemBuilder javaTwin = new ItemBuilder(Identifier.parse("mymod:via_property"));
        javaTwin.setMaxStackSize(16);
        javaTwin.setRarity("epic");
        String viaProperty = items.registered().get(0).fingerprint();
        assertEquals(new StartupRegistryRuntime(NODE).definitionFingerprint(javaTwin), viaProperty,
                "脚本 property 与显式 setter 必须产生同一指纹");
        assertEquals(64, viaProperty.length());
        assertNotNull(sink.built.get("minecraft:sound_event|mymod:boom"));
    }
    @Test
    void exampleRunsThroughTheProductionSequenceAndFullyDrains() {
        StartupRegistryRuntime runtime = runExample();
        assertTrue(runtime.isFullyDrained(), "示例的每条声明都在对应 pass 交付，无未交付残留");
        assertEquals(NODE, runtime.node());
    }
}
