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
 * 票 15 AC6：启动期注册的最小可运行示例链路——示例与
 * {@code docs/architecture-refactor/baseline/2026-09-15-registry-startup/examples/registry-startup.js}
 * 保持一致，按生产序列（STARTUP 脚本挂监听 → 首个注册表 pass 前恰好一次收集 → 逐注册表
 * 抽干到节点 sink）经真实 GraalJS 求值端到端跑通。
 *
 * <p>五种入口全覆盖：default 类型糖方法、命名类型、{@code custom}、裸 Supplier
 * {@code register(registry, id, supplier)}、setter/property parity；连带注册以
 * supplier 通道的 drain 结果观察（真实 Block→BlockItem 需 vanilla/FML，见 REPORT 遗留项）。
 * 示例只使用已通过 gate 的启动注册能力，不触碰服务器运行期 Dynamic Registry（独立生命周期）。
 */
class RegistryStartupMinimalExampleTest {

    private static final String NODE = "example-node";

    /** 与 baseline examples/registry-startup.js 的 STARTUP 段一致。 */
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
     * 双形状 sink：{@code deferred} 集合内的注册表按 NeoForge {@code RegisterEvent} 形状
     * 只记录 Supplier（平台在注册冻结前才执行——裸 JUnit 无 FML，ItemBuilder 构建需
     * vanilla 引导），其余立即执行（fabric 直注同款形状）。两种形状消费的都是 Runtime
     * 包装过的同一个 {@code validatedSupplier}。
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

    /** 生产序列：STARTUP 上下文里挂监听（EventGroupJS 生产同款绑定）→ 首 pass 前收集一次。 */
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
