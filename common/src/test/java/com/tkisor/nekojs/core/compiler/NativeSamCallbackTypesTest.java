package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.ScriptType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSamCallbackTypesTest {
    private final List<String> reported = new ArrayList<>();
    private ScriptBindingSchema.View view;

    public static final class Owner {
        public int getCount() { return 1; }
    }

    public static final class Context {
        public String getSide() { return "north"; }
    }

    public interface NativeProvider<Result, Input> {
        Result find(Input context, Owner owner);
        boolean equals(Object other);
        int hashCode();
        String toString();

        static void utility() {}
        default String description() { return "provider"; }
    }

    public interface DerivedProvider<Input> extends NativeProvider<String, Input> {}

    public interface ConcreteProvider {
        String find(Context context, Owner owner);
    }

    public interface MultipleMethods<Input> {
        String find(Input context);
        void reset();
    }

    public interface BroadResult {
        Object find(Context context);
    }

    public interface NarrowResult {
        String find(Context context);
    }

    public interface CovariantProvider extends BroadResult, NarrowResult {}

    public static final class NativeBinding {
        public void attach(String id, NativeProvider<String, Context> provider) {}
        public void inherited(String id, DerivedProvider<Context> provider) {}
        public void concrete(String id, ConcreteProvider provider) {}
        public void covariant(String id, CovariantProvider provider) {}
        public void unknownResult(String id, NativeProvider<?, Context> provider) {}
        public void ordinary(String id, BiFunction<Context, Owner, String> provider) {}
        public void notFunctional(String id, MultipleMethods<Context> provider) {}
    }

    @BeforeEach
    void setUp() {
        TestPlatformInit.ensureInitialized();
        ManagedCallbackSchemaRegistry.clear();
        view = new ScriptBindingSchema.View(Map.of("NativeSamCapabilities",
                new ScriptBindingSchema.BindingMembers(Set.of("attach", "inherited", "concrete", "covariant",
                        "unknownResult", "ordinary", "notFunctional"), Set.of(NativeBinding.class))), Set.of());
        ScriptErrorReporter.set((type, kind, failure) -> reported.add(failure.getMessage()));
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
    }

    private void validate(String source) {
        EventCallbackSourceValidator.validate(
                com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.STARTUP)
                        .resolve("native-sam-provider.js"), source, view);
    }

    @Test
    void ignoresObjectDefaultAndStaticMethodsAndUsesActualInputOrder() {
        validate("NativeSamCapabilities.attach('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCount(); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }

    @Test
    void nativeInputsStillRejectUnknownMembers() {
        validate("NativeSamCapabilities.attach('demo:energy', (context, owner) => {"
                + " context.getSidde(); owner.getCounnt(); })");
        assertEquals(2, reported.size(), reported.toString());
        assertTrue(reported.stream().anyMatch(message -> message.contains("getSidde")));
        assertTrue(reported.stream().anyMatch(message -> message.contains("getCounnt")));
    }

    @Test
    void nativeInputsStillRejectWrongMethodArity() {
        validate("NativeSamCapabilities.attach('demo:energy', (context, owner) => {"
                + " context.getSide('unexpected'); owner.getCount('unexpected'); })");
        assertEquals(2, reported.size(), reported.toString());
    }

    @Test
    void resolvesInheritedInterfaceTypeVariables() {
        validate("NativeSamCapabilities.inherited('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCount(); owner.getCounnt(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getCounnt"), reported.toString());
    }

    @Test
    void nonGenericInterfacesKeepConcreteInputTypes() {
        validate("NativeSamCapabilities.concrete('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCounnt(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getCounnt"), reported.toString());
    }

    @Test
    void covariantDeclarationsStillDescribeOneAbstractMethod() {
        validate("NativeSamCapabilities.covariant('demo:energy', context => {"
                + " context.getSide(); context.getSidde(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getSidde"), reported.toString());
    }

    @Test
    void unknownReturnTypeDoesNotDisableKnownInputChecks() {
        validate("NativeSamCapabilities.unknownResult('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCounnt(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getCounnt"), reported.toString());
    }

    @Test
    void standardBiFunctionRetainsBothKnownInputChecks() {
        validate("NativeSamCapabilities.ordinary('demo:energy', (context, owner) => {"
                + " context.getSide(); owner.getCounnt(); })");
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.getFirst().contains("getCounnt"), reported.toString());
    }

    @Test
    void multipleAbstractMethodsAreNotMistakenForAFunctionalInterface() {
        validate("NativeSamCapabilities.notFunctional('demo:energy', context => {"
                + " context.unknown(); })");
        assertTrue(reported.isEmpty(), reported.toString());
    }
}
