package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperValidator;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class LegacyTypeWrappersTest {
    @Test
    void dedicatedServerCollectsOnlyStartupAndServerHooks() {
        var collected = new ArrayList<ScriptType>();
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, false, wrappers -> {
            collected.add(wrappers.scriptType());
            if (wrappers.scriptType() == ScriptType.CLIENT) {
                throw new AssertionError("Dedicated server executed a client-only wrapper hook");
            }
            wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) ->
                    new PhaseValue(((KubeJSContext) context).getType(), input));
        });
        assertEquals(List.of(ScriptType.STARTUP, ScriptType.SERVER), collected);
        assertEquals(1, registry.view().size(), "phase definitions share one native adapter");
        var adapter = onlyAdapter(registry);
        withPhase(com.tkisor.nekojs.api.ScriptType.STARTUP, context -> assertTrue(adapter.test(context.eval("js", "'value'"))));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> assertTrue(adapter.test(context.eval("js", "'value'"))));
        withPhase(com.tkisor.nekojs.api.ScriptType.CLIENT, context -> assertFalse(adapter.test(context.eval("js", "'value'"))));
    }

    @Test
    void clientCollectionRoutesEachManagedProductionPhaseToItsOwnDefinition() {
        var collected = new ArrayList<ScriptType>();
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, wrappers -> {
            ScriptType declaration = wrappers.scriptType();
            collected.add(declaration);
            wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> {
                assertEquals(declaration, ((KubeJSContext) context).getType());
                return new PhaseValue(declaration, input);
            });
        });
        assertEquals(List.of(ScriptType.STARTUP, ScriptType.SERVER, ScriptType.CLIENT), collected);
        var adapter = onlyAdapter(registry);
        for (var phase : com.tkisor.nekojs.api.ScriptType.values()) {
            withPhase(phase, context -> {
                var value = context.eval("js", "'value'");
                if (phase == com.tkisor.nekojs.api.ScriptType.TEST) {
                    assertFalse(adapter.test(value));
                    assertTrue(assertThrows(IllegalStateException.class, () -> adapter.apply(value))
                            .getMessage().contains("NEKO-4263"));
                } else {
                    assertTrue(adapter.test(value));
                    var converted = assertInstanceOf(PhaseValue.class, adapter.apply(value));
                    assertEquals(ScriptType.valueOf(phase.name()), converted.phase());
                    assertEquals("value", converted.input());
                    assertFalse(adapter.test(context.eval("js", "() => 'value'")));
                }
            });
        }
    }

    @Test
    void missingPhaseDefinitionDeclinesInsteadOfStealingAnotherAdapterConversion() {
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, wrappers -> {
            if (wrappers.scriptType() == ScriptType.SERVER) {
                wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> new PhaseValue(ScriptType.SERVER, input));
            }
        });
        var adapter = onlyAdapter(registry);
        for (var phase : com.tkisor.nekojs.api.ScriptType.values()) {
            withPhase(phase, context -> {
                var value = context.eval("js", "'value'");
                assertEquals(phase == com.tkisor.nekojs.api.ScriptType.SERVER, adapter.test(value));
                if (phase != com.tkisor.nekojs.api.ScriptType.SERVER) {
                    assertTrue(assertThrows(IllegalStateException.class, () -> adapter.apply(value))
                            .getMessage().contains("NEKO-4263"));
                }
            });
        }
    }

    @Test
    void unmanagedOrNotEnteredContextDoesNotClaimValues() {
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, wrappers ->
                wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> new PhaseValue(wrappers.scriptType(), input)));
        var adapter = onlyAdapter(registry);
        withPhase(null, context -> {
            var value = context.eval("js", "'value'");
            assertFalse(adapter.test(value));
            assertTrue(assertThrows(IllegalStateException.class, () -> adapter.apply(value))
                    .getMessage().contains("NEKO-4263"));
        });
        try (Context context = newContext()) {
            ScriptContextRegistry.bind(context, com.tkisor.nekojs.api.ScriptType.SERVER);
            try {
                var value = context.eval("js", "'value'");
                assertFalse(adapter.test(value), "a value's owning phase is not an active conversion scope");
                assertTrue(assertThrows(IllegalStateException.class, () -> adapter.apply(value))
                        .getMessage().contains("NEKO-4263"));
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void acceptedInvalidInputAndInvalidWrapperResultsRemainFailures() {
        var failure = new IllegalArgumentException("Invalid wrapper input");
        var throwing = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(throwing, false, wrappers ->
                wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> { throw failure; }));
        var invalid = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(invalid, false, wrappers ->
                wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> null));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var value = context.eval("js", "'value'");
            var adapter = onlyAdapter(throwing);
            assertTrue(adapter.test(value));
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> adapter.apply(value)));
            var invalidAdapter = onlyAdapter(invalid);
            assertTrue(invalidAdapter.test(value));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> invalidAdapter.apply(value))
                    .getMessage().contains("NEKO-4261"));
        });
    }

    @Test
    void wrapperCannotMutateBindingsThroughBorrowedConversionContext() {
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, false, wrappers -> wrappers.register(PhaseValue.class, (dev.latvian.mods.rhino.Context context, Object input) -> {
            ((KubeJSContext) context).addToScope(null, "forbidden", input);
            return new PhaseValue(wrappers.scriptType(), input);
        }));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var adapter = onlyAdapter(registry);
            var value = context.eval("js", "'value'");
            assertTrue(adapter.test(value));
            assertTrue(assertThrows(UnsupportedOperationException.class, () -> adapter.apply(value))
                    .getMessage().contains("NEKO-4264"));
        });
    }

    @Test
    void phaseValidatorsDeclineWithoutInvokingFactoriesAndKeepNativeInputIdentity() {
        var registry = new JSTypeAdapterRegistry.Impl();
        var tested = new ArrayList<ScriptType>();
        var converted = new AtomicInteger();
        Object host = new Object();
        LegacyTypeWrappers.register(registry, true, wrappers -> {
            ScriptType declaration = wrappers.scriptType();
            wrappers.register(PhaseValue.class, (input, target) -> {
                tested.add(declaration);
                assertSame(PhaseValue.class, target.asClass());
                return input == host || declaration.name().equals(input);
            }, (context, input) -> {
                converted.incrementAndGet();
                assertEquals(declaration, ((KubeJSContext) context).getType());
                return new PhaseValue(declaration, input);
            });
        });
        var adapter = onlyAdapter(registry);
        for (var phase : List.of(com.tkisor.nekojs.api.ScriptType.STARTUP,
                com.tkisor.nekojs.api.ScriptType.SERVER, com.tkisor.nekojs.api.ScriptType.CLIENT)) {
            withPhase(phase, context -> {
                int calls = converted.get();
                int validations = tested.size();
                assertFalse(adapter.test(context.eval("js", "null")));
                assertFalse(adapter.test(context.eval("js", "'other'")));
                assertFalse(adapter.test(context.eval("js", "() => 'value'")));
                assertEquals(validations + 2, tested.size(), "callables never reach the validator");
                assertEquals(calls, converted.get(), "declining and testing never invoke the converter");
                var accepted = context.asValue(phase.name());
                assertTrue(adapter.test(accepted));
                assertEquals(calls, converted.get());
                assertEquals(phase.name(), assertInstanceOf(PhaseValue.class, adapter.apply(accepted)).input());
                var hostValue = context.asValue(host);
                assertTrue(adapter.test(hostValue));
                assertSame(host, assertInstanceOf(PhaseValue.class, adapter.apply(hostValue)).input());
                assertEquals(calls + 2, converted.get());
                assertEquals(ScriptType.valueOf(phase.name()), tested.getLast());
            });
        }
        int validations = tested.size();
        withPhase(com.tkisor.nekojs.api.ScriptType.TEST, context ->
                assertFalse(adapter.test(context.eval("js", "'SERVER'"))));
        withPhase(null, context -> assertFalse(adapter.test(context.eval("js", "'SERVER'"))));
        assertEquals(validations, tested.size(), "unmanaged phases never reach a validator");
    }

    @Test
    void validatorAndAcceptedFactoryFailuresPropagateWithoutCatchToReject() {
        var failure = new IllegalArgumentException("Validator rejected input exceptionally");
        var factoryFailure = new IllegalArgumentException("Accepted factory failed");
        var conversions = new AtomicInteger();
        var rejected = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(rejected, false, wrappers -> wrappers.register(PhaseValue.class,
                (input, target) -> { throw failure; }, (context, input) -> {
                    conversions.incrementAndGet();
                    return new PhaseValue(wrappers.scriptType(), input);
                }));
        var accepted = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(accepted, false, wrappers -> wrappers.register(PhaseValue.class,
                TypeWrapperValidator.ALWAYS_VALID, (context, input) -> { throw factoryFailure; }));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var input = context.eval("js", "'value'");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> onlyAdapter(rejected).test(input)));
            assertEquals(0, conversions.get());
            assertTrue(onlyAdapter(accepted).test(input));
            assertSame(factoryFailure, assertThrows(IllegalArgumentException.class, () -> onlyAdapter(accepted).apply(input)));
        });
    }

    @Test
    void directValidatorOverloadAndDuplicateRegistrationPreserveOriginalDefinition() {
        var registry = new TypeWrapperRegistry(ScriptType.SERVER);
        TypeWrapperValidator validator = (input, target) -> input != null;
        DirectTypeWrapperFactory<PhaseValue> factory = input -> new PhaseValue(ScriptType.SERVER, input);
        registry.register(PhaseValue.class, validator, factory);
        assertTrue(registry.hasTypeWrapper(PhaseValue.class));
        var original = registry.definitions().get(PhaseValue.class);
        assertSame(validator, registry.validators().get(PhaseValue.class));
        assertThrows(UnsupportedOperationException.class, () -> registry.validators().clear());
        var failure = assertThrows(IllegalArgumentException.class, () -> registry.register(PhaseValue.class,
                TypeWrapperValidator.ALWAYS_VALID, factory));
        assertTrue(failure.getMessage().contains("NEKO-4262"));
        assertSame(original, registry.definitions().get(PhaseValue.class));
        assertSame(validator, registry.validators().get(PhaseValue.class));
        var nativeRegistry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(nativeRegistry, false, wrappers ->
                wrappers.register(PhaseValue.class, validator, factory));
        withPhase(com.tkisor.nekojs.api.ScriptType.SERVER, context -> {
            var adapter = onlyAdapter(nativeRegistry);
            assertFalse(adapter.test(context.eval("js", "null")));
            assertTrue(adapter.test(context.eval("js", "'value'")));
            assertEquals("value", assertInstanceOf(PhaseValue.class, adapter.apply(context.eval("js", "'value'"))).input());
        });
    }

    private static JSTypeAdapter<?> onlyAdapter(JSTypeAdapterRegistry.Impl registry) {
        assertEquals(1, registry.view().size());
        return registry.view().iterator().next();
    }

    private static Context newContext() {
        return Context.newBuilder("js").allowAllAccess(true).option("engine.WarnInterpreterOnly", "false").build();
    }

    private static void withPhase(com.tkisor.nekojs.api.ScriptType phase, Consumer<Context> action) {
        try (Context context = newContext()) {
            if (phase != null) ScriptContextRegistry.bind(context, phase);
            context.enter();
            try {
                action.accept(context);
            } finally {
                context.leave();
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    private record PhaseValue(ScriptType phase, Object input) {}
}
