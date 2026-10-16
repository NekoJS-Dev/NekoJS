package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"rawtypes", "unchecked"})
class TypeReflectorReturnContractTest {
    public static class Data<T> {}

    @RemapByPrefix("neko$")
    public interface DataContract<T> { Data<T> neko$data(); }
    public interface HostContract extends DataContract<RawHost>, WideContract {}
    public static class RawHost implements HostContract {
        public Data neko$data() { return new Data(); }
    }
    public static class GenericHost<T> implements DataContract<T> {
        public Data neko$data() { return new Data(); }
    }
    @RemapByPrefix("neko$")
    public static class InheritedHost extends GenericHost<String> {
        public Data neko$data(int marker) { return new Data(); }
    }
    public interface SameContract { Data<RawHost> neko$data(); }
    public static class DuplicateHost extends RawHost implements SameContract {
        public Data neko$data() { return new Data(); }
    }
    public interface TextContract { Data<String> neko$data(); }
    public static class ConflictingHost extends RawHost implements TextContract {
        public Data neko$data() { return new Data(); }
    }
    public static class TypedHost implements DataContract<CharSequence> {
        public Data<CharSequence> neko$data() { return new Data<>(); }
    }
    public static class NoContract { public Data neko$data() { return new Data(); } }
    public static class RawContract implements DataContract {
        public Data neko$data() { return new Data(); }
    }
    public interface MethodContract { <T extends Number> Data<T> neko$data(); }
    public static class MethodHost implements MethodContract {
        public Data neko$data() { return new Data(); }
    }
    public interface WideContract { Object neko$data(); }
    public static class NarrowHost implements WideContract {
        public Data neko$data() { return new Data(); }
    }

    @Test
    void restoresInjectedReturnFromContextualInterfaceWithoutChangingNames() {
        MethodDecl method = data(RawHost.class);
        assertEquals(List.of(TypeReflector.toRef(RawHost.class)), method.returnType.ref.arguments());
        assertEquals("data", method.effectiveName());
        assertFalse(method.returnType.overridden);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(new TypeReflector().reflect(RawHost.class));
        assertTrue(output.contains("data(): $TypeReflectorReturnContractTest$Data<$TypeReflectorReturnContractTest$RawHost>;"), output);
    }

    @Test
    void retainsInScopeClassVariablesAndResolvesInheritedArguments() {
        assertEquals(List.of(ApiTypeRef.typeVariable("T")), data(GenericHost.class).returnType.ref.arguments());
        assertEquals(List.of(ApiTypeRef.primitive("string")), data(InheritedHost.class).returnType.ref.arguments());
    }

    @Test
    void acceptsRepeatedEquivalentContractsButRejectsConflictingOnes() {
        assertEquals(List.of(TypeReflector.toRef(RawHost.class)), data(DuplicateHost.class).returnType.ref.arguments());
        assertTrue(data(ConflictingHost.class).returnType.ref.arguments().isEmpty());
    }

    @Test
    void preservesExistingTypedImplementation() throws Exception {
        assertEquals(TypedHost.class.getMethod("neko$data").getGenericReturnType(), data(TypedHost.class).returnType.sourceType);
    }

    @Test
    void retainsRawReturnWhenContractIsAbsentRawMethodGenericOrErasedDifferently() {
        for (Class<?> host : List.of(NoContract.class, RawContract.class, MethodHost.class, NarrowHost.class)) {
            assertEquals(Data.class, data(host).returnType.sourceType, host.getName());
            assertTrue(data(host).returnType.ref.arguments().isEmpty(), host.getName());
        }
    }

    private static MethodDecl data(Class<?> host) {
        return new TypeReflector().reflect(host).methods.stream()
                .filter(method -> method.name.equals("neko$data") && method.params.isEmpty())
                .findFirst().orElseThrow();
    }
}
