//? if >=26 {
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;

import graal.graalvm.polyglot.Value;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RegistryAutoAdapterScanner}：发现/去重/跳过语义。
 *
 * <p>合成 holder 类（裸 JVM）测字段过滤与去重；vanilla 注册表真值路径经
 * {@link VanillaRegistryProbe} assume-skip——裸 JVM 无 FML Loader 时跳过而非失败。
 */
class RegistryAutoAdapterScannerTest {

    /** 合成 holder：覆盖各种应跳过的字段形态（裸 JVM 可跑，不触碰注册表内容）。 */
    static class SyntheticHolders {
        public static final java.util.Set<String> NOT_A_REGISTRY = java.util.Set.of();

        public static final net.minecraft.core.Registry<String> NULL_REGISTRY = null;

        @SuppressWarnings("rawtypes")
        public static final net.minecraft.core.Registry RAW_GENERIC = null;

        @SuppressWarnings("unused")
        public static final net.minecraft.core.Registry<String> NOT_PUBLIC_EVEN_IF_IT_WERE = null;

        private final net.minecraft.core.Registry<String> instanceField = null;
    }

    @Test
    void skipsNullRawAndNonRegistryFieldsOnBareJvm() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        RegistryAutoAdapterScanner.installInto(registry, List.of(SyntheticHolders.class));

        assertTrue(registry.view().isEmpty(), "nothing should be registered from synthetic holders");
    }

    @Test
    void manuallyCoveredTargetsAreNotDuplicated() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        registry.register(new ItemAdapter());

        RegistryAutoAdapterScanner.installInto(registry, List.of(BuiltInRegistries.class));

        long itemAdapters = registry.view().stream()
                .filter(adapter -> adapter.getTargetClass() == net.minecraft.world.item.Item.class)
                .count();
        assertEquals(1, itemAdapters, "手写 ItemAdapter 不得被自动适配器重复");
    }

    @Test
    void uncoveredVanillaRegistryTypeGainsStringIdAdapter() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        RegistryAutoAdapterScanner.installInto(registry, List.of(BuiltInRegistries.class));

        JSTypeAdapter<?> fluid = registry.view().stream()
                .filter(adapter -> adapter.getTargetClass() == Fluid.class)
                .findFirst()
                .orElse(null);
        assertNotNull(fluid, "BuiltInRegistries.FLUID 应自动补出适配器");

        Object water = fluid.apply(Value.asValue("water"));
        assertNotNull(water);
        assertTrue(Fluid.class.isInstance(water));
    }

    @Test
    void dynamicAdaptersUseLowestPrecedence() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        RegistryAutoAdapterScanner.installInto(registry, List.of(BuiltInRegistries.class));

        for (JSTypeAdapter<?> adapter : registry.view()) {
            if (adapter.getTargetClass() == Fluid.class) {
                assertEquals(com.tkisor.nekojs.api.data.ConversionPrecedence.LOWEST, adapter.getPrecedence());
            }
        }
    }

    /** ResourceKey 形态 holder：vanilla Registries 类的字段形态（经根注册表解析）。 */
    static class SyntheticKeyHolder {
        public static final ResourceKey<Registry<Fluid>> FLUID = Registries.FLUID;
    }

    /** 根注册表里不存在的 key：裸 JVM 应静默跳过。 */
    static class SyntheticUnresolvableKeyHolder {
        public static final ResourceKey<Registry<String>> NOT_A_REAL_REGISTRY =
                ResourceKey.create(
                        ResourceKey.createRegistryKey(Identifier.tryParse("nekojs:no_such_root")),
                        Identifier.tryParse("nekojs:no_such_registry"));
    }

    @Test
    void unresolvableResourceKeyFieldsAreSkippedOnBareJvm() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        RegistryAutoAdapterScanner.installInto(registry, List.of(SyntheticUnresolvableKeyHolder.class));

        assertTrue(registry.view().isEmpty(), "根注册表解析不到的 ResourceKey 字段应被静默跳过");
    }

    @Test
    void resourceKeyFieldsResolveAndDedupAgainstRegistryHolders() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        // Registries（ResourceKey 形态）与 BuiltInRegistries（Registry 形态）覆盖同一批
        // vanilla 类型——去重集合保证 Fluid 只注册一次
        RegistryAutoAdapterScanner.installInto(registry,
                List.of(BuiltInRegistries.class, SyntheticKeyHolder.class));

        long fluidAdapters = registry.view().stream()
                .filter(adapter -> adapter.getTargetClass() == Fluid.class)
                .count();
        assertEquals(1, fluidAdapters, "同一注册表类型经两种 holder 形态只应注册一次");
    }
}
//?}
