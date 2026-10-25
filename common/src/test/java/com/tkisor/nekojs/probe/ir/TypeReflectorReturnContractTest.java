package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public static class Key<T> { public T value; }
    public interface AttachmentContract {
        <A> A getAttached(Key<A> key);
        <A> A setAttached(Key<A> key, A value);
    }
    public static class ErasedAttachmentHost implements AttachmentContract {
        public Object getAttached(Key key) { return null; }
        public Object setAttached(Key key, Object value) { return value; }
    }
    public interface EquivalentAttachmentContract {
        <Value> Value getAttached(Key<Value> key);
        <Value> Value setAttached(Key<Value> key, Value value);
    }
    public static class RepeatedAttachmentHost extends ErasedAttachmentHost implements EquivalentAttachmentContract {
        public Object getAttached(Key key) { return null; }
        public Object setAttached(Key key, Object value) { return value; }
    }
    public interface ConflictingMethodContract { <T extends CharSequence> Data<T> neko$data(); }
    public static class ConflictingMethodHost implements MethodContract, ConflictingMethodContract {
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
    void restoresWholeMethodGenericContractWithoutChangingImplementationIdentity() throws Exception {
        TypeDecl host = new TypeReflector().reflect(ErasedAttachmentHost.class);
        for (String name : List.of("getAttached", "setAttached")) {
            MethodDecl method = host.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            assertEquals(List.of("A"), method.typeParams);
            assertEquals(ApiTypeRef.typeVariable("A"), method.returnType.ref);
            assertEquals(List.of(ApiTypeRef.typeVariable("A")), method.params.getFirst().type.ref.arguments());
            assertEquals(ErasedAttachmentHost.class, method.sourceMethod.getDeclaringClass());
            assertFalse(method.returnType.overridden);
            if (name.equals("setAttached")) {
                assertEquals(ApiTypeRef.typeVariable("A"), method.params.get(1).type.ref);
            }
        }
        MethodDecl bounded = data(MethodHost.class);
        assertEquals(List.of("T"), bounded.typeParams);
        assertEquals(List.of(TypeReflector.toRef(Number.class)),
                bounded.typeParameterBounds.get("T").stream().map(slot -> slot.ref).toList());
        assertEquals(List.of(ApiTypeRef.typeVariable("T")), bounded.returnType.ref.arguments());
    }

    @Test
    void retainsRawReturnWhenContractIsAbsentRawOrErasedDifferently() {
        for (Class<?> host : List.of(NoContract.class, RawContract.class, NarrowHost.class)) {
            assertEquals(Data.class, data(host).returnType.sourceType, host.getName());
            assertTrue(data(host).returnType.ref.arguments().isEmpty(), host.getName());
        }
    }

    @Test
    void comparesMethodVariablesByPositionButRejectsDifferentBounds() {
        TypeDecl repeated = new TypeReflector().reflect(RepeatedAttachmentHost.class);
        for (String name : List.of("getAttached", "setAttached")) {
            MethodDecl method = repeated.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            assertEquals(1, method.typeParams.size());
            assertEquals(ApiTypeRef.typeVariable(method.typeParams.getFirst()), method.returnType.ref);
        }
        assertTrue(data(ConflictingMethodHost.class).typeParams.isEmpty());
        assertEquals(Data.class, data(ConflictingMethodHost.class).returnType.sourceType);
    }

    @Test
    void recoveredMethodsKeepTypedCallsThroughThePublicGenerator() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> classes = List.of(Key.class, AttachmentContract.class, ErasedAttachmentHost.class);
        String packageName = Key.class.getPackageName();
        Set<String> names = classes.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet());
        for (Class<?> type : classes) {
            generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        }
        String declaration = generator.generate(packageName,
                classes.stream().map(type -> type.getName().substring(packageName.length() + 1)).toList(), List.of(), names);
        Path output = Path.of("build/probe-ts-erased-method-contract");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declaration);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"skipLibCheck":false,"noEmit":true,"target":"ES2022","module":"ESNext","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type {
                    $TypeReflectorReturnContractTest$Key as Key,
                    $TypeReflectorReturnContractTest$AttachmentContract as AttachmentContract,
                    $TypeReflectorReturnContractTest$ErasedAttachmentHost as ErasedHost
                } from 'java:com/tkisor/nekojs/probe/ir';
                declare const key: Key<string>;
                declare const host: ErasedHost;
                const contract: AttachmentContract = host;
                const read: string = host.getAttached(key);
                const write: string = host.setAttached(key, 'replacement');
                // @ts-expect-error A typed key does not produce an unrelated value.
                const wrong: number = host.getAttached(key);
                // @ts-expect-error The key determines the accepted payload type.
                host.setAttached(key, 7);
                """);
    }

    private static MethodDecl data(Class<?> host) {
        return new TypeReflector().reflect(host).methods.stream()
                .filter(method -> method.name.equals("neko$data") && method.params.isEmpty())
                .findFirst().orElseThrow();
    }
}
