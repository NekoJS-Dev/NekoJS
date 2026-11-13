package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
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
 * <p>All five entry points are covered: the default type sugar method, an explicit named
 * type, {@code custom}, a bare Supplier {@code register(registry, id, supplier)}, and
 * setter/property parity. Co-registration is observed through the drain result on the
 * supplier channel; a real Block to BlockItem conversion needs vanilla/FML. The example
 * only uses startup registration that already passed the gate and leaves the server-side
 * Dynamic Registry alone, since that one has its own lifecycle.
 */
class RegistryStartupMinimalExampleTest {

    private static final String NODE = "example-node";

    /** Matches the STARTUP section of the baseline examples/registry-startup.js. */
    static final String STARTUP_EXAMPLE = """
            // startup_scripts/registry.js —— 启动期声明注册（票 15 示例）
            RegistryEvents.register(event => {
              // 1) default 类型糖方法（免类型名）
              event.soundEvent('mymod:boom', b => { b.fixedRange = 16 });
              // 2) 命名类型显式传入
              event.soundEvent('mymod:ping', 'basic', b => { b.setFixedRange(32) });
              // 3) custom：按全局唯一类型名解析注册表
              event.custom('mymod:scholar_variant', 'art', b => { });
              // 4) 裸 Supplier：高级入口，Runtime 校验返回值/实际类型/重复 ID
              event.register('minecraft:villager_type', 'mymod:scholar', () => 'raw-scholar');
              // 5) setter/property parity：两种写法同一个 setter、同一指纹
              event.item('mymod:via_property', b => { b.maxStackSize = 16; b.rarity = 'EPIC' });
              event.item('mymod:via_setter', b => { b.setMaxStackSize(16); b.setRarity('epic') });
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

    private static RegistryInfosPoint.RegistryInfos exampleInfos() {
        return RegistryInfosPoint.scan(new RegistryInfosPoint.RegistryInfosCollector());
    }

    private static RegistryTypesPoint.RegistryTypes exampleTypes() {
        RegistryTypesPoint.RegistryTypesCollector collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(Registries.SOUND_EVENT, "basic");
        collector.registerType(Registries.VILLAGER_TYPE, "art", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.registerType(Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.setDefault(Registries.VILLAGER_TYPE, "basic");
        collector.registerType(Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.setDefault(Registries.ITEM, "basic");
        return new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
    }

    /** Runs the production sequence: installs the listener in a STARTUP context (the same
     * EventGroupJS binding production uses), then collects once before the first pass. */
    private static StartupRegistryRuntime runExample() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.STARTUP);
            try {
                context.getBindings("js").putMember("RegistryEvents", new EventGroupJS(RegistryEvents.GROUP, ScriptType.STARTUP));
                context.eval("js", STARTUP_EXAMPLE);
                runtime.collectOnce(new RegistryEventJS(runtime.repository(), NODE, exampleInfos(), exampleTypes()));
                RecordingSink sink = new RecordingSink(java.util.Set.of(Registries.ITEM, Registries.VILLAGER_TYPE));
                StartupRegistryRuntime.DrainResult items = runtime.drainFor(Registries.ITEM, sink);
                StartupRegistryRuntime.DrainResult sounds = runtime.drainFor(Registries.SOUND_EVENT, sink);
                StartupRegistryRuntime.DrainResult villagers = runtime.drainFor(Registries.VILLAGER_TYPE, sink);
                verify(items, sounds, villagers, sink);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
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
