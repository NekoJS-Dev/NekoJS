package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ApiTypeRefPyRenderer} and {@link PythonClassRenderer}: type mapping and IR to .pyi rendering.
 */
class PythonRendererTest {

    @Test
    void heritageOrderingUsesEditedTransitiveSymbolsAndKeepsUnrelatedOrder() {
        TypeDecl root = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Root");
        TypeDecl middle = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Middle");
        TypeDecl leaf = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Leaf");
        TypeDecl independent = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Independent");
        TypeDecl child = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Child");
        middle.interfaces.add(symbolSlot(root.fqn));
        leaf.interfaces.add(symbolSlot(middle.fqn));
        child.interfaces.addAll(List.of(symbolSlot(root.fqn), symbolSlot(independent.fqn), symbolSlot(leaf.fqn)));
        var declarations = List.of(root, middle, leaf, independent, child);
        var available = Set.of(root.fqn, middle.fqn, leaf.fqn, independent.fqn, child.fqn);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        assertTrue(renderer.render(child).contains("class Child(Independent, Leaf, Root):"));
        assertEquals(List.of("java:pkg.Root", "java:pkg.Independent", "java:pkg.Leaf"),
                child.interfaces.stream().map(slot -> slot.ref.name()).toList());
        middle.interfaces.clear();
        assertTrue(renderer.render(child).contains("class Child(Root, Independent, Leaf):"));
    }

    @Test
    void heritageOrderingDoesNotInferThroughMissingHiddenOrCyclicDeclarations() {
        TypeDecl root = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Root");
        TypeDecl middle = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Middle");
        TypeDecl leaf = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "pkg.Leaf");
        TypeDecl child = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Child");
        middle.interfaces.add(symbolSlot(root.fqn));
        leaf.interfaces.add(symbolSlot(middle.fqn));
        child.interfaces.addAll(List.of(symbolSlot(root.fqn), symbolSlot(leaf.fqn)));
        var types = new ApiTypeRefPyRenderer(Set.of(root.fqn, middle.fqn, leaf.fqn, child.fqn));
        assertTrue(new PythonClassRenderer(types, List.of(root, leaf, child)).render(child)
                .contains("class Child(Root, Leaf):"));
        middle.hidden = true;
        assertTrue(new PythonClassRenderer(types, List.of(root, middle, leaf, child)).render(child)
                .contains("class Child(Root, Leaf):"));
        middle.hidden = false;
        root.interfaces.add(symbolSlot(leaf.fqn));
        assertTrue(new PythonClassRenderer(types, List.of(root, middle, leaf, child)).render(child)
                .contains("class Child(Root, Leaf):"));
    }

    private static TypeSlot symbolSlot(String fqn) {
        return new TypeSlot(null, ApiTypeRef.symbol(new ApiSymbolId("java", fqn)));
    }

    @Test
    void associatedPayloadMethodsPreserveEveryOverloadAndLeaveSingleMethodsUndecorated() {
        TestPlatformInit.ensureInitialized();
        var members = List.of(
                new RegistryBuilderSurfaceEntry.Member("item", RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                        "item(id: string, build: (build: FixtureBuilder) => void): FixtureBuilder",
                        "def item(self, id: str, build: Callable[[FixtureBuilder], None]) -> FixtureBuilder: ..."),
                new RegistryBuilderSurfaceEntry.Member("item", RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                        "item(id: string, typeName: string, build: (build: FixtureBuilder) => void): FixtureBuilder",
                        "def item(self, id: str, typeName: str, build: Callable[[FixtureBuilder], None]) -> FixtureBuilder: ..."),
                new RegistryBuilderSurfaceEntry.Member("register", RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                        "register(registry: string, id: string, supplier: () => unknown): unknown",
                        "def register(self, registry: str, id: str, supplier: Callable[[], Any]) -> Any: ..."));
        var builder = new RegistryBuilderSurfaceEntry("FixtureBuilder", "test:item", "basic", "item", List.of(), "",
                new RegistryBuilderSurfaceEntry.EventPayload(Sample.class, "FixtureRegistryEvent", members));
        var event = EventCatalogEntry.of("FixtureEvents", "register", ScriptTypePredicate.exact(ScriptType.STARTUP),
                Sample.class, null, false, false);
        String output = new PythonEventRenderer(new ApiTypeRefPyRenderer(Set.of()), Set.of())
                .render(ScriptType.STARTUP, List.of(event), Map.of(), List.of(builder));
        assertEquals(2, countOccurrences(output, "    @overload\n    def item("), output);
        assertFalse(output.contains("    @overload\n    def register("), output);
        assertTrue(output.contains("from typing import Any, Callable, overload, Protocol"), output);
        assertTrue(output.contains("typeName: str, build: Callable[[FixtureBuilder], None]"), output);
    }

    /** Reflected sample: instance field, getter, instance methods, static method, constructors and Java overloads. */
    public static class Sample {
        public String nameField;
        public Sample() {}
        public Sample(String name) { this.nameField = name; }   // constructor overload
        public String getName() { return nameField; }   // getter → @property name
        public void greet(String who) {}
        public void greet(String who, int times) {}     // overloads greet(String)
        public static int helper(int n) { return n; }
    }

    public static class JavaOnlyNames {
        public void nekojs$applyScripts() {}
        public int get2DigitYearStart() { return 0; }
        public void set2DigitYearStart(int value) {}
        public void applyScripts() {}
    }

    public interface JavaOnlyInterface {
        void loader$invoke();
        void invoke();
    }

    @Test
    void javaOnlyMembersDoNotInventPythonAliasesOrInvalidBeanProperties() {
        TypeReflector reflector = new TypeReflector();
        TypeDecl declaration = reflector.reflect(JavaOnlyNames.class);
        PythonClassRenderer renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn)));
        String output = renderer.render(declaration);
        assertFalse(output.contains("def nekojs$applyScripts"), output);
        assertFalse(output.contains("def nekojs_applyScripts"), output);
        assertFalse(output.contains("def 2DigitYearStart"), output);
        assertFalse(output.contains("@2DigitYearStart.setter"), output);
        assertTrue(output.contains("def get2DigitYearStart(self) -> int"), output);
        assertTrue(output.contains("def set2DigitYearStart(self, value: int) -> None"), output);
        assertTrue(output.contains("def applyScripts(self) -> None"), output);
        String interfaceOutput = renderer.render(reflector.reflect(JavaOnlyInterface.class));
        assertFalse(interfaceOutput.contains("def loader$invoke"), interfaceOutput);
        assertFalse(interfaceOutput.contains("def loader_invoke"), interfaceOutput);
        assertTrue(interfaceOutput.contains("def invoke(self) -> None"), interfaceOutput);
    }

    // -------------------- ApiTypeRefPyRenderer --------------------

    @Test
    void render_primitives() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of());
        assertEquals("str", r.render(ApiTypeRef.primitive("string")));
        assertEquals("str", r.render(ApiTypeRef.primitive("char")));
        assertEquals("bool", r.render(ApiTypeRef.primitive("boolean")));
        assertEquals("int", r.render(ApiTypeRef.primitive("int")));
        assertEquals("float", r.render(ApiTypeRef.primitive("double")));
        assertEquals("Any", r.render(ApiTypeRef.primitive("object")));
        assertEquals("None", r.render(ApiTypeRef.voidType()));
    }

    @Test
    void render_arrayUnionTypeVar() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of());
        assertEquals("list[str]", r.render(ApiTypeRef.array(ApiTypeRef.primitive("string"))));
        // ApiTypeRef sorts union members by compatibilityKey when it builds the union: int < string, so "int | str"
        assertEquals("int | str",
                r.render(ApiTypeRef.union(java.util.List.of(ApiTypeRef.primitive("string"), ApiTypeRef.primitive("int")))));
        assertEquals("Any", r.render(ApiTypeRef.typeVariable("T")));
    }

    @Test
    void render_symbolAvailability() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of("net.x.Foo"));
        assertEquals("Foo", r.render(ApiTypeRef.symbol(new ApiSymbolId("java", "net.x.Foo"))));
        // A SYMBOL that was not collected renders as Any, so the stub has no dangling reference
        assertEquals("Any", r.render(ApiTypeRef.symbol(new ApiSymbolId("java", "net.x.Bar"))));
    }

    @Test
    void simplePyName_nestedDollarToUnderscore() {
        assertEquals("Outer_Inner", ApiTypeRefPyRenderer.simplePyName("pkg.Outer$Inner"));
        assertEquals("Foo", ApiTypeRefPyRenderer.simplePyName("net.x.Foo"));
    }

    // -------------------- PythonClassRenderer --------------------

    @Test
    void renderClass_emitsFieldsGetterMethodsCtor() {
        TypeReflector reflector = new TypeReflector();
        TypeDecl d = reflector.reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        String out = classR.render(d);
        // class declaration (a nested class name ends in _Sample)
        assertTrue(out.contains("class "), "class header: " + out);
        assertTrue(out.trim().startsWith("class "), out);
        // instance field
        assertTrue(out.contains("nameField: str"), out);
        // getter → @property + def name(self) -> str
        assertTrue(out.contains("@property"), out);
        assertTrue(out.contains("def name(self) -> str"), out);
        // instance method
        assertTrue(out.contains("def greet(self,"), out);
        assertTrue(out.contains(": str"), out);   // greet's String parameter
// static method
        assertTrue(out.contains("@staticmethod"), out);
        assertTrue(out.contains("def helper("), out);
        assertTrue(out.contains("-> int"), out);  // helper returns int
// constructor becomes __init__
        assertTrue(out.contains("def __init__(self) -> None"), out);
    }

    @Test
    void renderClass_emitsMethodAndFieldDocs() {
        TypeReflector reflector = new TypeReflector();
        TypeDecl d = reflector.reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        // class-level docs
        d.docs.add("Class doc line1");
        d.docs.add("Class doc line2");
        // constructor docs
        d.constructors.get(0).docs.add("Creates a sample.");
        // getter docs (handled the same way for @property)
        MethodDecl getter = d.methods.stream().filter(m -> m.isGetter).findFirst().orElseThrow();
        getter.docs.add("The name.");
        // instance method docs (multiple lines): they go on the one-arg greet; any instance in the overload group works,
        // since the assertion matches the one-arg signature
        MethodDecl greet = d.methods.stream()
                .filter(m -> m.name.equals("greet") && m.params.size() == 1)
                .findFirst()
                .orElseThrow();
        greet.docs.add("Greets someone.");
        greet.docs.add("Second line.");
        // field docs (multiple lines: first line as a trailing comment, later lines prefixed with `# `)
        d.fields.get(0).docs.add("Field doc.");
        d.fields.get(0).docs.add("Second field line.");

        String out = classR.render(d);
        // class-level docstring (multiple lines)
        assertTrue(out.contains("    \"\"\"Class doc line1\nClass doc line2\"\"\""), out);
        // constructor docstring: it starts on a new line after the def line, indented 8 spaces
        assertTrue(out.contains("def __init__(self) -> None:\n        \"\"\"Creates a sample.\"\"\"\n        ..."), out);
        // getter（@property）docstring
        assertTrue(out.contains("def name(self) -> str:\n        \"\"\"The name.\"\"\"\n        ..."), out);
        // instance method docstring (multiple lines); the root build.gradle enables -parameters, so the name is the real who
        assertTrue(out.contains("def greet(self, who: str) -> None:\n        \"\"\"Greets someone.\nSecond line.\"\"\"\n        ..."), out);
        // field docs: a trailing `  # ...` comment on the declaration line, then later lines prefixed with `# `
        assertTrue(out.contains("nameField: str  # Field doc.\n    # Second field line."), out);
    }

    @Test
    void renderClass_javaOverloadsUseOverloadDecorator() {
        // Sample has greet(String)/greet(String, int) overloads plus two constructors, so every def of the same name is marked
        // @overload; otherwise Pylance reports that a declaration is obscured by a declaration with the same name
        TypeDecl d = new TypeReflector().reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        String out = classR.render(d);
        assertEquals(4, countOccurrences(out, "@overload"),
                "two greet overloads + two __init__ overloads must all carry @overload: " + out);
        assertTrue(out.contains("@overload\n    def greet(self, who: str) -> None"), out);
        assertTrue(out.contains("@overload\n    def greet(self, who: str, times: int) -> None"), out);
        assertTrue(out.contains("@overload\n    def __init__(self) -> None"), out);
        assertTrue(out.contains("@overload\n    def __init__(self, name: str) -> None"), out);

        assertTrue(PythonClassRenderer.hasOverloads(d));
        assertFalse(PythonClassRenderer.hasOverloads(new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Solo")));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // -------------------- ClassEditor：renameClass / addMethod --------------------

    @Test
    void renameClass_usesNewPyName() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Foo");
        editorFor(d).renameClass("Renamed");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Foo"))).render(d);
        assertTrue(out.contains("class Renamed:"), out);
        assertFalse(out.contains("class Foo"), "old class name should be gone: " + out);
    }

    @Test
    void renameClass_enumSelfRefsFollowInPy() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.ENUM, null, "pkg.Color");
        FieldDecl red = new FieldDecl("RED", TypeSlot.of(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.Color"))));
        red.isStatic = true;
        red.isEnumConstant = true;
        d.fields.add(red);

        editorFor(d).renameClass("Color2");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Color"))).render(d);
        assertTrue(out.contains("class Color2:"), out);
        assertTrue(out.contains("RED: Color2"), "enum constant self-ref must follow rename: " + out);
    }

    @Test
    void addMethod_emitsPythonDef() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Foo");
        // "a:string" is a named parameter, "int" is a bare type (its name becomes arg1); static methods have no self
        editorFor(d).addMethod("foo", "boolean", "a:string", "int");
        editorFor(d).addStaticMethod("create", "string");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Foo"))).render(d);
        assertTrue(out.contains("def foo(self, a: str, arg1: int) -> bool: ..."), out);
        assertTrue(out.contains("@staticmethod"), out);
        assertTrue(out.contains("def create() -> str: ..."), out);
    }

    /** Obtains a ClassEditor through the event: its constructor is package-private, so other packages must use forClass. */
    private static com.tkisor.nekojs.probe.events.ClassEditor editorFor(TypeDecl d) {
        return new ProbeModifyTypeEventJS(Map.of(d.fqn, d)).forClass(d.fqn);
    }
}
