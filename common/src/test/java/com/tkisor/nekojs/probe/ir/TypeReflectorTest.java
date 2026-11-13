package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JS name remapping and bridge filtering in {@link TypeReflector}: the names the probe declares must match
 * the runtime Graal remapper ({@code JavaMemberIndex.remapName}).
 */
class TypeReflectorTest {

    public interface OverloadParent<T> {
        T decode(T value);
        static String factory() { return "parent"; }
        @HideFromJS
        default T decode(T value, boolean hidden) { return value; }
    }

    public interface OverloadChild<U> extends OverloadParent<U> {
        U decode(String value, int marker);
    }

    @Test
    void childInterfaceRetainsActualInheritedOverloadsWithItsOwnTypeVariables() throws Exception {
        TypeDecl child = new TypeReflector().reflect(OverloadChild.class);
        List<MethodDecl> overloads = child.methods.stream().filter(method -> method.name.equals("decode")).toList();
        assertEquals(2, overloads.size());
        MethodDecl inherited = overloads.stream().filter(method -> method.params.size() == 1).findFirst().orElseThrow();
        assertEquals("U", inherited.params.getFirst().type.ref.name());
        assertEquals("U", inherited.returnType.ref.name());
        assertTrue(child.methods.stream().noneMatch(method -> method.name.equals("factory")));
        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        String output = renderer.render(child);
        assertTrue(output.contains("decode(value: U): U;"), output);
        assertTrue(output.contains("decode(value: string, marker: number): U;"), output);
        java.nio.file.Path generated = java.nio.file.Path.of("build/probe-ts-interface-overloads/types.d.ts");
        java.nio.file.Files.createDirectories(generated.getParent());
        java.nio.file.Files.writeString(generated, "declare module 'probe-overload-fixture' {\n"
                + renderer.render(new TypeReflector().reflect(OverloadParent.class)) + output + "}\n");
    }

    @RemapByPrefix("neko$")
    public static class RemapSample {
        public String neko$data() { return "x"; }
        public String neko$getId() { return "id"; }
        public void neko$setId(String id) {}
        public String plainMethod() { return "p"; }
        @HideFromJS
        public void hiddenMethod() {}
    }

    /** Covariant return override on an interface: javac emits an Object bridge with the same parameters and a different
     * return type, and it must be filtered out. */
    public interface BaseSpec {
        default Object value() { return null; }
    }

    public interface ConcreteSpec extends BaseSpec {
        @Override
        String value();
    }

    @Test
    void remapByPrefixStripsPrefixIntoRenameTo() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);

        MethodDecl data = method(decl, "neko$data");
        assertEquals("data", data.effectiveName(), "neko$data must remap to data");
        assertFalse(data.isGetter, "data is not getter-shaped");

        MethodDecl plain = method(decl, "plainMethod");
        assertEquals("plainMethod", plain.effectiveName());
        assertNull(plain.renameTo, "plain method must keep null renameTo (byte-identical legacy output)");
    }

    @Test
    void getterSetterDetectionUsesRemappedName() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);

        // neko$getId becomes getId, the getter for property id, matching runtime Graal getter semantics (a script reads .id)
        MethodDecl getter = decl.methods.stream()
                .filter(m -> m.isGetter && "id".equals(m.property))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected getter for property id in " + decl.methods));
        assertEquals("getId", getter.effectiveName());

        // neko$setId becomes setId and pairs with the getter as the setter
        MethodDecl setter = decl.methods.stream()
                .filter(m -> m.isSetter && m.effectiveName().equals("setId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected setter setId in " + decl.methods));
        assertEquals(1, setter.params.size());
    }

    @Test
    void hideFromJsExcludesMethod() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);
        // @HideFromJS methods never reach the IR (jsName == null skips them), matching runtime visibility
        assertTrue(decl.methods.stream().noneMatch(m -> m.name.equals("hiddenMethod")),
                "@HideFromJS methods must be excluded from probe output: " + decl.methods);
    }

    @Test
    void interfaceBridgeMethodsAreFiltered() {
        TypeDecl decl = new TypeReflector().reflect(ConcreteSpec.class);

        // ConcreteSpec.value() covariant override makes javac emit an Object value() bridge: same parameters,
        // different return type, which is an invalid overload in a Python stub; filter it, keep String value().
        List<MethodDecl> values = decl.methods.stream().filter(m -> m.effectiveName().equals("value")).toList();
        assertEquals(1, values.size(), "bridge must be filtered: " + decl.methods);
        assertEquals("string", values.get(0).returnType.ref.name(), "non-bridge declaration must win");
    }

    // ==================== mixin / interface-injection host class ====================

    /** Injected interface. The annotation sits on the interface and the host class implements it; after mixin injection the
     * methods are reflected from the host class. */
    @RemapByPrefix("neko$")
    public interface InjectedSpec {
        Object neko$data();
    }

    public static class InjectedHost implements InjectedSpec {
        @Override
        public String neko$data() { return "x"; }
        public void neko$setId(String id) {}
    }

    public static class GenericBase<T> {}

    public static class StringChild extends GenericBase<String> {}

    @Test
    void genericSuperclassKeepsResolvedArguments() {
        TypeDecl decl = new TypeReflector().reflect(StringChild.class);

        assertTrue(decl.superType.sourceType instanceof java.lang.reflect.ParameterizedType,
                "generic superclass reflection must keep its actual type arguments");
        assertEquals("string", decl.superType.ref.arguments().get(0).name(),
                "generic superclass IR must retain the inherited argument");

        String out = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(decl);
        assertTrue(out.contains("extends $TypeReflectorTest$GenericBase<string>"), out);
    }

    @Test
    void hostClassMethodsRemapViaInjectedInterfaceAnnotation() {
        // At runtime Graal reaches @RemapByPrefix through the interface method path and gets the JS name data.
        // Probing reflects the host class methods (declaringClass=InjectedHost), which must remap the same way
        // so that the declaration matches runtime.
        TypeDecl decl = new TypeReflector().reflect(InjectedHost.class);

        MethodDecl data = decl.methods.stream()
                .filter(m -> m.effectiveName().equals("data"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("neko$data must remap to data: " + decl.methods));
        assertEquals("neko$data", data.name, "java name preserved");
        // Bridge filtering: only one declaration survives for neko$data (the Object bridge is dropped)
        assertEquals(1, decl.methods.stream().filter(m -> m.effectiveName().equals("data")).count(),
                "bridge must be filtered on host classes too: " + decl.methods);
    }

    private static MethodDecl method(TypeDecl decl, String javaName) {
        return decl.methods.stream()
                .filter(m -> m.name.equals(javaName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("method " + javaName + " not found in " + decl.methods));
    }
}
