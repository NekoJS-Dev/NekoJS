package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FunctionalInterfaceAliasGeneratorTest {

    public interface GenericMapper<T, R> {
        R map(T value);
    }

    public interface StringMapper extends GenericMapper<String, String> {}

    public interface BroadValue {
        Object value();
    }

    public interface CovariantValue extends BroadValue {
        @Override
        String value();
    }

    public interface GenericMethod {
        <U> List<U[]> map(List<U[]> values);
    }

    public static final class NestedInputApi {
        public interface Supplier<T> {
            T get();
        }

        public static final class Payload {}

        public static final class Api {
            public void accept(Supplier<Supplier<Payload>> callback) {}
        }
    }

    public interface Growing<T> {
        Growing<List<T>> next();
    }

    public static final class GrowingApi {
        public void accept(Growing<String> callback) {}
    }

    @Test
    void emitsResolvedSamAliasesForComparatorInheritedAndCovariantMethods() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        Set<String> types = Set.of(java.util.Comparator.class.getName(), StringMapper.class.getName(),
                CovariantValue.class.getName());
        generator.prepare(types, Set.of());

        String comparator = generator.declaration(java.util.Comparator.class.getName());
        String inherited = generator.declaration(StringMapper.class.getName());
        String covariant = generator.declaration(CovariantValue.class.getName());

        assertTrue(comparator.contains("CallbackArg0 = Host0, CallbackArg1 = Host0"), comparator);
        assertFalse(comparator.contains("CallbackArg2"), "Comparator.equals(Object) is not an extra SAM: " + comparator);
        assertTrue(inherited.contains("CallbackArg0 = string, CallbackResult = string"), inherited);
        assertTrue(covariant.contains("CallbackResult = string"), covariant);
    }

    @Test
    void iterableRetainsItsLegacyArrayAliasInsteadOfAnUnsupportedSamAlias() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        generator.prepare(Set.of(Iterable.class.getName()), Set.of());

        assertNull(generator.declaration(Iterable.class.getName()));
    }

    public interface IteratorSupplier { java.util.Iterator<String> get(); }

    @Test
    void iteratorCallbackResultsRetainTheHostIteratorContract() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        generator.prepare(Set.of(IteratorSupplier.class.getName(), java.util.Iterator.class.getName()), Set.of());
        String declaration = generator.declaration(IteratorSupplier.class.getName());
        assertTrue(declaration.contains("CallbackResult = $Iterator<string>"), declaration);
    }

    @Test
    void methodVariablesFallBackToAnyAndRawOrHiddenAliasesStaySafe() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        generator.prepare(Set.of(GenericMethod.class.getName(), List.class.getName()), Set.of());

        String method = generator.declaration(GenericMethod.class.getName());
        assertFalse(method.contains("U"), method);
        assertTrue(method.contains("CallbackArg0 = $List<any[]>"), method);
        assertTrue(method.contains("CallbackResult = any[][]"), method);

        generator.prepare(Set.of(java.util.function.Function.class.getName()), Set.of());
        String raw = generator.declaration(java.util.function.Function.class.getName());
        assertTrue(raw.contains("Host0 = any, Host1 = any, CallbackArg0 = Host0, CallbackResult = Host1"), raw);

        String functionName = java.util.function.Function.class.getName();
        registry.registerClassAlias(functionName, "$ManualFunction_");
        generator.prepare(Set.of(functionName), Set.of());
        assertNull(generator.declaration(functionName), "explicit adapter aliases take precedence");

        registry.clear();
        generator.prepare(Set.of(functionName), Set.of(functionName));
        assertNull(generator.declaration(functionName), "hidden interfaces must not leave aliases behind");
    }

    @Test
    void nestedSameInterfaceArgumentsKeepTheirInputAliasAtEachLevel() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        Set<String> generated = Set.of(NestedInputApi.Supplier.class.getName(),
                NestedInputApi.Payload.class.getName(), NestedInputApi.Api.class.getName());
        generator.prepare(generated, Set.of());

        String output = new TypeScriptClassRenderer(registry)
                .render(new TypeReflector().reflect(NestedInputApi.Api.class));
        String supplier = "$FunctionalInterfaceAliasGeneratorTest$NestedInputApi$Supplier";
        String payload = "$FunctionalInterfaceAliasGeneratorTest$NestedInputApi$Payload";
        String expected = supplier + "_<" + supplier + "<" + payload + ">, "
                + supplier + "_<" + payload + ", " + payload + ">>";
        assertTrue(output.contains(expected), output);
    }

    @Test
    void expandingRecursiveGenericSamFallsBackToHostAtBoundedDepth() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        FunctionalInterfaceAliasGenerator generator = new FunctionalInterfaceAliasGenerator(registry);
        generator.prepare(Set.of(Growing.class.getName(), GrowingApi.class.getName(), List.class.getName()), Set.of());

        String aliasDeclaration = generator.declaration(Growing.class.getName());
        assertTrue(aliasDeclaration.contains("CallbackResult = $FunctionalInterfaceAliasGeneratorTest$Growing<$List<Host0>>"),
                aliasDeclaration);
        String output = new TypeScriptClassRenderer(registry).render(new TypeReflector().reflect(GrowingApi.class));

        assertTrue(output.contains("$FunctionalInterfaceAliasGeneratorTest$Growing_<"), output);
        assertTrue(output.contains("$FunctionalInterfaceAliasGeneratorTest$Growing<$List<"), output);
        assertTrue(output.length() < 20_000, "recursive generic expansion must remain bounded");
    }
}
