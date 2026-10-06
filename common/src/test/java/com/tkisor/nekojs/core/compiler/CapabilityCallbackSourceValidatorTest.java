package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityCallbackSourceValidatorTest {
    private final List<String> reported = new ArrayList<>();
    private ScriptBindingSchema.View view;

    public static final class Owner {
        public int getCount() {
            return 1;
        }
    }

    public interface BlockProvider<T, C> {
        T find(Object level, Object position, Object state, Owner owner, C context);
    }

    public static final class CapabilityEvent {
        public void registerItem(String id, String capability, BiFunction<Owner, Object, ?> provider) {}
        public void registerEntity(String id, String capability, BiFunction<Owner, Object, ?> provider) {}
        public <T, C> void registerBlockNative(String id, Object capability, BlockProvider<T, C> provider) {}
        public void registerBlockEntity(String id, String capability, Supplier<?> provider) {}
    }

    @BeforeEach
    void setUp() {
        TestPlatformInit.ensureInitialized();
        ManagedCallbackSchemaRegistry.clear();
        EventGroup group = EventGroup.of("CapabilityPreflightEvents");
        group.startup("register", CapabilityEvent.class);
        EventSchemaRegistry.registerGroup(group);
        view = new ScriptBindingSchema.View(Map.of("CapabilityPreflightEvents",
                new ScriptBindingSchema.BindingMembers(Set.of("register"))), Set.of());
        ScriptErrorReporter.set((type, kind, failure) -> reported.add(failure.getMessage()));
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    private void validate(String source) {
        EventCallbackSourceValidator.validate(
                com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.STARTUP)
                        .resolve("capability-provider.js"), source, view);
    }

    @Test
    void conditionalBiFunctionProviderDoesNotBecomeExtraRegistrationArguments() {
        validate("CapabilityPreflightEvents.register(event => {"
                + " event.registerItem('minecraft:stick', 'energy',"
                + " (stack, context) => context === null ? null : energy(stack));"
                + " event.registerEntity('minecraft:pig', 'fluid',"
                + " (entity, side) => side === north ? fluids(entity) : null); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void unannotatedNativeProviderWithFiveParametersAndConditionalBodyIsAccepted() {
        validate("CapabilityPreflightEvents.register(event => {"
                + " event.registerBlockNative('minecraft:grass_block', capability,"
                + " (level, position, state, owner, side) =>"
                + " side === north ? storage(level, position) : null); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void supplierAndBlockBodyProvidersAreAccepted() {
        validate("CapabilityPreflightEvents.register(event => {"
                + " event.registerBlockEntity('minecraft:furnace', 'energy', () => storage);"
                + " event.registerItem('minecraft:stick', 'energy', (stack, context) => {"
                + " return context === null ? null : energy(stack); }); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void genuinelyWrongRegistrationArityIsStillRejected() {
        validate("CapabilityPreflightEvents.register(event => {"
                + " event.registerItem('minecraft:stick', (stack, context) => energy(stack)); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("registerItem"), reported.toString());
    }

    public static final class Context {
        public String getSide() {
            return "north";
        }
    }

    public interface NativeProvider<T, C> {
        T find(C context, Owner owner);
    }

    public static final class NativeBinding {
        public void attach(String id, NativeProvider<String, Context> provider) {}
    }

    @Test
    void unannotatedSamUsesInputSignatureRatherThanReturnTypeArguments() {
        view = new ScriptBindingSchema.View(Map.of("NativeCapabilities",
                new ScriptBindingSchema.BindingMembers(Set.of("attach"), Set.of(NativeBinding.class))), Set.of());
        validate("NativeCapabilities.attach('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCount(); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void unannotatedSamStillChecksKnownCallbackParameterMembers() {
        view = new ScriptBindingSchema.View(Map.of("NativeCapabilities",
                new ScriptBindingSchema.BindingMembers(Set.of("attach"), Set.of(NativeBinding.class))), Set.of());
        validate("NativeCapabilities.attach('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCounnt(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getCounnt"), reported.toString());
    }

    @Test
    void unknownRegistrationMethodIsStillRejected() {
        validate("CapabilityPreflightEvents.register(event => {"
                + " event.registerIetm('minecraft:stick', 'energy',"
                + " (stack, context) => energy(stack)); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("registerIetm"), reported.toString());
        assertTrue(reported.getFirst().contains("Did you mean"), reported.toString());
    }
}
