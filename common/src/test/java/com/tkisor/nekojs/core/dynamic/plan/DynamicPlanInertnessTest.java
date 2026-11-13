package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Structural</b> evidence that the candidate plan surface cannot, at the type level, modify the
 * live registry, attach production callbacks or publish public bindings.
 *
 * <p>It complements the behavior assertions in {@code DynamicCandidatePlanSemanticsTest} and
 * {@code DynamicRegistryReloadPipelineTest}: those prove that this candidate wrote no ledger entries
 * and attached no callbacks, while this test proves that even broken code could not write anything.
 * None of the public members of the plan, the store or the adapter request exposes a platform
 * execution channel (Supplier/Consumer/Function/Runnable/ProxyExecutable/Binding) or a
 * Minecraft/loader type, and the only action an adapter request carries is {@code register}.
 *
 * <p>The module-level guarantee is the same fact one level out: {@code common} does not depend on
 * Minecraft or the loader at compile time ({@code :common:checkCommonIsolation} plus guardLint
 * L1/L2), and the plan package lives in common. This test narrows that fact to the signatures of the
 * plan package itself.
 */
class DynamicPlanInertnessTest {

    /** Public surface of the plan package, including the observation types the facade reads plans through. */
    private static final List<Class<?>> PLAN_TYPES = List.of(
            DynamicCandidateRegistryPlan.class,
            DynamicDefinition.class,
            DynamicDefinitionType.class,
            DynamicDefinitionBuilder.class,
            DynamicItemBuilder.class,
            DynamicSoundEventBuilder.class,
            DynamicMobEffectBuilder.class,
            DynamicAdapterRequest.class,
            DynamicRegistryPlanStore.class,
            DynamicBuilderContract.class,
            DynamicBuilderSurface.class,
            DynamicBuilderSurfaces.class,
            DynamicRegistrationBookkeeping.class);

    @Test
    void planTypesCarryNoExecutionChannelToAnyPlatformAdapter() {
        for (Class<?> type : PLAN_TYPES) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(isExecutionChannel(field.getType()),
                        type.getSimpleName() + "." + field.getName() + " must not be an execution channel");
            }
            for (Method method : type.getDeclaredMethods()) {
                assertFalse(isExecutionChannel(method.getReturnType()),
                        type.getSimpleName() + "#" + method.getName() + " must not return an execution channel");
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertFalse(isExecutionChannel(parameter),
                            type.getSimpleName() + "#" + method.getName()
                                    + " must not accept an execution channel");
                }
            }
        }
    }

    @Test
    void planTypesReferenceNoMinecraftOrLoaderType() {
        for (Class<?> type : PLAN_TYPES) {
            assertNoPlatformType(type.getName());
            for (Field field : type.getDeclaredFields()) {
                assertNoPlatformType(field.getGenericType().getTypeName());
            }
            for (Method method : type.getDeclaredMethods()) {
                // toGenericString also covers parameterized types, such as the List<DynamicAdapterRequest> type argument.
                assertNoPlatformType(method.toGenericString());
            }
        }
        // The only external shape it depends on is common's own catalog entry, the renderer's input, not an MC type.
        assertEquals("com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry",
                RegistryBuilderSurfaceEntry.class.getName(),
                "计划包只依赖 common 结构化条目承载声明派生输入");
    }

    @Test
    void adapterRequestExposesNoOperationOtherThanRegister() {
        for (Method method : DynamicAdapterRequest.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getName().equals("equals")
                    || method.getName().equals("hashCode") || method.getName().equals("toString")) {
                continue;
            }
            assertTrue(method.getParameterCount() == 0 || method.getReturnType() == void.class,
                    "Adapter 请求只承载数据（record 访问器/构造）：" + method.toGenericString());
            assertFalse(isExecutionChannel(method.getReturnType()),
                    "Adapter 请求没有执行通道：" + method.toGenericString());
        }
        for (Constructor<?> constructor : DynamicAdapterRequest.class.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertFalse(isExecutionChannel(parameter),
                        "Adapter 请求构造器不得接受执行通道：" + constructor);
                assertNoPlatformType(parameter.getName());
            }
        }
        assertEquals(2, DynamicAdapterRequest.class.getDeclaredConstructors().length,
                "构造器只有数据装配两条（record 规范构造器 + 由定义派生的便捷构造器），无第二执行入口");
        DynamicAdapterRequest request = new DynamicAdapterRequest(7L, "minecraft:item", "mymod:ruby",
                DynamicRegisterMode.WORLD, "server_scripts/main.js", "fp", DynamicAdapterRequest.ACTION_REGISTER,
                List.of("maxStackSize=64"));
        assertEquals("register", request.action());
        assertEquals(7L, request.generation(), "generation-scoped");
        assertNotNull(request.registryKey());
    }

    @Test
    void frozenCandidateSurfaceIsExactlyTheThreeVerifiedTypes() {
        assertEquals(3, DynamicDefinitionType.values().length,
                "候选范围封闭为 Item/SoundEvent/MobEffect（不存在第四类或通用 catalog 入口）");
        assertEquals(java.util.Set.of("minecraft:item", "minecraft:sound_event", "minecraft:mob_effect"),
                DynamicDefinition.supportedRegistryKeys());
        assertEquals("[item, soundEvent, mobEffect]", DynamicDefinitionType.apiNameDirectory(),
                "成员目录冻结为票面三个类型直达入口名");
        // An unknown type name does not resolve to a registrable type, including a registry key passed off as a type name.
        assertTrue(DynamicDefinitionType.byApiName("entity_type").isEmpty());
        assertTrue(DynamicDefinitionType.byApiName("minecraft:item").isEmpty());
        assertTrue(DynamicDefinitionType.byApiName(null).isEmpty());
    }

    private static boolean isExecutionChannel(Class<?> type) {
        return Supplier.class.isAssignableFrom(type)
                || Consumer.class.isAssignableFrom(type)
                || Function.class.isAssignableFrom(type)
                || Runnable.class.isAssignableFrom(type)
                || ProxyExecutable.class.isAssignableFrom(type)
                || com.tkisor.nekojs.api.data.Binding.class.isAssignableFrom(type);
    }

    private static void assertNoPlatformType(String signature) {
        assertFalse(signature.contains("net.minecraft"), "计划包不得引用 MC 类型：" + signature);
        assertFalse(signature.contains("net.neoforged"), "计划包不得引用 NeoForge 类型：" + signature);
        assertFalse(signature.contains("net.fabricmc"), "计划包不得引用 Fabric 类型：" + signature);
    }
}
