package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperValidator;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class NativeTypeWrapperFactoryTest {
    @Test
    void exposesExactRhinoFactoryParentAndKubeRegistryOverloadDescriptors() throws Exception {
        Class<?> factory = Class.forName("dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory");
        assertTrue(factory.isInterface());
        assertTrue(factory.isAnnotationPresent(FunctionalInterface.class));
        assertEquals(1, Arrays.stream(factory.getMethods()).filter(method -> Modifier.isAbstract(method.getModifiers())).count());
        var wrap = factory.getDeclaredMethod("wrap", Context.class, Object.class, TypeInfo.class);
        assertTrue(Modifier.isAbstract(wrap.getModifiers()));
        assertEquals("(Ldev/latvian/mods/rhino/Context;Ljava/lang/Object;Ldev/latvian/mods/rhino/type/TypeInfo;)Ljava/lang/Object;",
                MethodType.methodType(wrap.getReturnType(), wrap.getParameterTypes()).descriptorString());
        assertTrue(factory.isAssignableFrom(DirectTypeWrapperFactory.class));
        var direct = DirectTypeWrapperFactory.class.getDeclaredMethod("wrap", Context.class, Object.class, TypeInfo.class);
        assertTrue(direct.isDefault());
        assertEquals(Object.class, direct.getReturnType());
        assertEquals(Object.class, DirectTypeWrapperFactory.class.getMethod("wrap", Object.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class, factory).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class,
                TypeWrapperValidator.class, factory).getReturnType());
        Class<?> registries = Class.forName("dev.latvian.mods.kubejs.script.TypeWrapperRegistry$RegistriesFromFunction");
        assertTrue(registries.isInterface());
        assertTrue(BiFunction.class.isAssignableFrom(registries));
        assertEquals(Object.class, registries.getMethod("apply", Object.class, Object.class).getReturnType());
        assertEquals(void.class, TypeWrapperRegistry.class.getMethod("register", Class.class, registries).getReturnType());
    }

    @BeforeAll
    static void bootstrapNativeRegistries() {
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryReceivesTheSameRawTargetAsValidatorInEachManagedPhase() {
        var validatedTargets = new EnumMap<ScriptType, TypeInfo>(ScriptType.class);
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, wrappers -> {
            ScriptType declaredPhase = wrappers.scriptType();
            wrappers.register(Wrapped.class, (input, target) -> {
                assertEquals(Wrapped.class, target.asClass());
                validatedTargets.put(declaredPhase, target);
                return input instanceof String;
            }, (context, input, target) -> {
                assertEquals(declaredPhase, ((KubeJSContext) context).getType());
                assertSame(validatedTargets.get(declaredPhase), target);
                assertThrows(IndexOutOfBoundsException.class, () -> target.param(0));
                return new Wrapped(declaredPhase, input);
            });
        });
        var adapter = onlyAdapter(registry);
        for (ScriptType phase : ScriptType.values()) {
            withPhase(com.tkisor.nekojs.api.ScriptType.valueOf(phase.name()), context -> {
                var input = context.eval("js", "'native'");
                assertTrue(adapter.test(input));
                assertEquals(new Wrapped(phase, "native"), adapter.apply(input));
                assertFalse(adapter.test(context.eval("js", "() => 'native'")));
            });
        }
        withPhase(com.tkisor.nekojs.api.ScriptType.TEST, context ->
                assertFalse(adapter.test(context.eval("js", "'native'"))));
    }

    @Test
    void validatorRefusalDoesNotExecuteCanonicalFactory() {
        var conversions = new AtomicInteger();
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, false, wrappers -> wrappers.register(Wrapped.class,
                (input, target) -> false, (context, input, target) -> {
                    conversions.incrementAndGet();
                    return new Wrapped(wrappers.scriptType(), input);
                }));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            assertFalse(onlyAdapter(registry).test(context.eval("js", "'declined'")));
            assertEquals(0, conversions.get());
        });
    }

    @Test
    void validatorAndCanonicalFactoryFailuresPreserveTheirIdentity() {
        var validatorFailure = new IllegalArgumentException("expected validator failure");
        var factoryFailure = new AssertionError("expected factory failure");
        var validatorRegistry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(validatorRegistry, false, wrappers -> wrappers.register(Wrapped.class,
                (input, target) -> { throw validatorFailure; }, (context, input, target) -> {
                    fail("A failed validator cannot enter conversion");
                    return null;
                }));
        var factoryRegistry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(factoryRegistry, false, wrappers -> wrappers.register(Wrapped.class,
                TypeWrapperValidator.ALWAYS_VALID, (context, input, target) -> { throw factoryFailure; }));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var input = context.eval("js", "'native'");
            assertSame(validatorFailure, assertThrows(IllegalArgumentException.class,
                    () -> onlyAdapter(validatorRegistry).test(input)));
            assertTrue(onlyAdapter(factoryRegistry).test(input));
            assertSame(factoryFailure, assertThrows(AssertionError.class,
                    () -> onlyAdapter(factoryRegistry).apply(input)));
        });
    }

    @Test
    void directFactoryDefaultForwardsToItsSingleArgumentContract() {
        Object input = new Object();
        DirectTypeWrapperFactory<Object> direct = value -> value;
        TypeWrapperFactory<Object> canonical = direct;
        assertSame(input, canonical.wrap(new Context(), input, TypeInfo.of(Object.class)));
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, false, wrappers -> wrappers.register(Wrapped.class,
                (DirectTypeWrapperFactory<Wrapped>) value -> new Wrapped(wrappers.scriptType(), value)));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var value = context.eval("js", "'direct'");
            assertTrue(onlyAdapter(registry).test(value));
            assertEquals(new Wrapped(ScriptType.SERVER, "direct"), onlyAdapter(registry).apply(value));
        });
    }

    @Test
    void definitionViewsPreserveOldIdentityAndCanonicalDuplicatesStayAtomic() {
        var registry = new TypeWrapperRegistry(ScriptType.STARTUP);
        TypeWrapperRegistry.ContextFromFunction<Wrapped> original = (context, value) -> new Wrapped(ScriptType.STARTUP, value);
        registry.register(Wrapped.class, original);
        var oldSnapshot = registry.definitions();
        assertSame(original, oldSnapshot.get(Wrapped.class));
        assertThrows(UnsupportedOperationException.class, oldSnapshot::clear);
        var originalFactory = registry.factories().get(Wrapped.class);
        assertThrows(UnsupportedOperationException.class, () -> registry.factories().clear());
        TypeWrapperFactory<Wrapped> replacement = (context, value, target) -> new Wrapped(ScriptType.SERVER, value);
        var duplicate = assertThrows(IllegalArgumentException.class, () -> registry.register(Wrapped.class, replacement));
        assertTrue(duplicate.getMessage().contains("NEKO-4262"));
        assertSame(original, registry.definitions().get(Wrapped.class));
        assertSame(originalFactory, registry.factories().get(Wrapped.class));
        registry.register(String.class, (TypeWrapperFactory<String>) (context, value, target) -> value.toString());
        assertEquals(1, oldSnapshot.size());
        assertEquals(2, registry.definitions().size());
        assertTrue(registry.hasTypeWrapper(String.class));
        assertEquals("native", registry.definitions().get(String.class).apply(new Context(), "native"));
    }

    @Test
    void registryFactoriesBorrowBeforeRunningAndServerStillRejectsMissingService() {
        var calls = new AtomicInteger();
        var received = new AtomicReference<RegistryAccessContainer>();
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, wrappers -> wrappers.register(Wrapped.class,
                (TypeWrapperRegistry.RegistriesFromFunction<Wrapped>) (registries, input) -> {
                    calls.incrementAndGet();
                    received.set(registries);
                    return new Wrapped(wrappers.scriptType(), input);
                }));
        var adapter = onlyAdapter(registry);
        for (var phase : new com.tkisor.nekojs.api.ScriptType[] {
                com.tkisor.nekojs.api.ScriptType.STARTUP, com.tkisor.nekojs.api.ScriptType.CLIENT }) {
            withPhase(phase, context -> {
                var input = context.eval("js", "42");
                assertTrue(adapter.test(input));
                var result = assertInstanceOf(Wrapped.class, adapter.apply(input));
                assertEquals(ScriptType.valueOf(phase.name()), result.phase());
                assertEquals(42, ((Number) result.input()).intValue());
                assertSame(RegistryAccessContainer.BUILTIN, received.get());
            });
        }
        assertEquals(2, calls.get());
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var input = context.eval("js", "42");
            assertTrue(adapter.test(input));
            var failure = assertThrows(UnsupportedOperationException.class, () -> adapter.apply(input));
            assertTrue(failure.getMessage().contains("NEKO-4282"));
            assertEquals(2, calls.get(), "A registry function must not run before borrowing succeeds");
        });
    }

    private static JSTypeAdapter<?> onlyAdapter(JSTypeAdapterRegistry.Impl registry) {
        assertEquals(1, registry.view().size());
        return registry.view().iterator().next();
    }

    private static void withPhase(com.tkisor.nekojs.api.ScriptType phase,
            Consumer<graal.graalvm.polyglot.Context> action) {
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js").allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false").build()) {
            ScriptContextRegistry.bind(context, phase);
            context.enter();
            try {
                action.accept(context);
            } finally {
                context.leave();
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    private record Wrapped(ScriptType phase, Object input) {}
}
