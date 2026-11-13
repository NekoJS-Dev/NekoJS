package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering stability regression: for a set of representative classes, checks that the output of
 * reflecting with TypeReflector and rendering with TypeScriptClassRenderer is stable and agrees with
 * the IR structure. Byte-level end-to-end coverage lives in {@code TypeScriptNoopIrGoldenTest} (an
 * ir=null run and an ir=unedited-list run produce identical trees) and
 * {@code ProbeOutputCompatibilityTest} (legacy artifacts still exist).
 *
 * <p>Covers plain classes, final classes, generic classes, interfaces (including nested ones),
 * enums, and classes with getters and setters.
 */
class TypeScriptClassRendererTest {

    private static final List<Class<?>> SAMPLES = List.of(
            java.util.ArrayList.class,        // generic class
            java.lang.String.class,           // final class with many methods
            java.util.List.class,             // generic interface
            java.lang.Runnable.class,         // simple interface
            java.util.Map.class,              // generic interface
            java.util.concurrent.TimeUnit.class, // enum
            java.io.File.class,               // many getters and setters
            java.util.Date.class,             // getters such as getTime
            java.util.HashMap.class,          // generic class
            java.lang.Number.class            // abstract class
    );

    @Test
    void identityInputsRetainHostTypesAndExplicitAdaptersStillOverrideThem() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        for (String fqn : List.of("java.io.File", "java.nio.file.Path", "java.util.UUID")) {
            String name = "$" + fqn.substring(fqn.lastIndexOf('.') + 1);
            assertEquals(name, TypeScriptClassRenderer.renderTypeRef(symbol(fqn), aliases, true));
            assertEquals(name, TypeScriptClassRenderer.renderTypeRef(symbol(fqn), aliases, false));
            aliases.registerClassAlias(fqn, "string | " + name);
            assertEquals("string | " + name, TypeScriptClassRenderer.renderTypeRef(symbol(fqn), aliases, true));
            aliases.clear();
            assertEquals(name, TypeScriptClassRenderer.renderTypeRef(symbol(fqn), aliases, true));
        }
        String file = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(java.io.File.class));
        assertTrue(file.contains("implements $Serializable, $Comparable<$File>"), file);
        assertTrue(file.contains("compareTo(arg0: $File): number;"), file);
        assertFalse(file.contains("compareTo(arg0: string)"), file);
    }

    @Test
    void rendererOutputIsStableForAllSamples() {
        for (Class<?> cls : SAMPLES) {
            TypeReflector reflector = new TypeReflector();
            TypeDecl decl = reflector.reflect(cls);

            TypeAliasRegistry aliases1 = new TypeAliasRegistry();
            TypeScriptClassRenderer renderer1 = new TypeScriptClassRenderer(aliases1);
            String first = renderer1.render(decl);

            // Rendering the same reflected IR twice gives the same output (the renderer keeps no state)
            TypeAliasRegistry aliases2 = new TypeAliasRegistry();
            TypeScriptClassRenderer renderer2 = new TypeScriptClassRenderer(aliases2);
            String second = renderer2.render(decl);
            assertEquals(first, second, "repeated render must be deterministic for " + cls.getName());

            // Basic shape checks on the rendered text
            assertTrue(first.startsWith("    export "), "decl must start with export block: " + cls.getName());
            assertTrue(first.trim().endsWith("}") || first.trim().endsWith("};"),
                    "declarations must close the class/interface or its static object surface: " + cls.getName());
        }
    }

    @Test
    void iterableConsumerWildcardUsesIterableElementAsCallbackArgument() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        new FunctionalInterfaceAliasGenerator(aliases).prepare(
                Set.of(Iterable.class.getName(), java.util.function.Consumer.class.getName()), Set.of());

        TypeDecl iterable = new TypeReflector().reflect(Iterable.class);
        String output = new TypeScriptClassRenderer(aliases).render(iterable);

        assertTrue(output.contains("forEach(arg0: $Consumer_<T, T>): void;"), output);
    }

    @Test
    void functionAndThenRetainsCallerClassAndMethodVariables() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        new FunctionalInterfaceAliasGenerator(aliases).prepare(
                Set.of(java.util.function.Function.class.getName()), Set.of());

        String output = new TypeScriptClassRenderer(aliases)
                .render(new TypeReflector().reflect(java.util.function.Function.class));

        assertTrue(output.contains("andThen<V>("), output);
        assertTrue(output.contains("$Function_<R, V, R, V>"), output);
        assertFalse(output.contains("$Function_<any, any, any, any>"), output);
    }

    // ---------------- Deterministic getter/setter candidate selection ----------------

    /** Covariant getter base: the subclass overrides the Object return with String, so javac emits a
     * bridge returning Object. */
    public abstract static class CovariantGetterBase {
        public abstract Object getValue();
    }

    /** Covariant override: getValue() returns String, and the synthetic bridge getValue() returns Object. */
    public static class CovariantGetterSub extends CovariantGetterBase {
        @Override
        public String getValue() { return "1"; }
    }

    @Test
    void covariantGetterWinsOverItsBridge() {
        TypeDecl decl = new TypeReflector().reflect(CovariantGetterSub.class);

        List<MethodDecl> getters = decl.methods.stream().filter(m -> m.isGetter && "value".equals(m.property)).toList();
        assertEquals(1, getters.size(), "duplicate getter candidates must be deduped to one");
        // The non-bridge candidate (the covariant override) wins; picking the bridge would render the wrong supertype return
        assertEquals(String.class, getters.get(0).returnType.sourceType,
                "covariant override must win over its bridge method");

        // String renders as "string" and Object as "object", so the two candidates are distinguishable
        String out = render(decl);
        assertTrue(out.contains("get value(): string;"), out);
        assertFalse(out.contains("get value(): object;"), out);
    }

    /** getFoo() and isFoo() coexist: two candidates for one property, and the get form wins
     * deterministically by signature-key order. */
    public static class GetIsCoexistence {
        public String getActive() { return "yes"; }
        public boolean isActive() { return true; }
    }

    @Test
    void coexistingGetAndIsFormsPickDeterministically() {
        TypeDecl decl = new TypeReflector().reflect(GetIsCoexistence.class);

        List<MethodDecl> getters = decl.methods.stream().filter(m -> m.isGetter && "active".equals(m.property)).toList();
        assertEquals(1, getters.size(), "duplicate getter candidates must be deduped to one");
        assertEquals(String.class, getters.get(0).returnType.sourceType,
                "get-form must win over is-form for the same property");

        String out = render(decl);
        assertTrue(out.contains("get active(): string;"), out);
    }

    /** Overloaded setters with the same name: the paired parameter slot is chosen deterministically,
     * non-bridge first, then by parameter type name. */
    public static class OverloadedSetter {
        public Object getLevel() { return 0; }
        public void setLevel(Number level) { }
        public void setLevel(Integer level) { }
    }

    @Test
    void overloadedSetterParamSlotIsDeterministic() {
        TypeDecl decl = new TypeReflector().reflect(OverloadedSetter.class);

        MethodDecl getter = decl.methods.stream()
                .filter(m -> m.isGetter && "level".equals(m.property)).findFirst().orElseThrow();
        assertNotNull(getter.setterParamType, "paired setter must be found");
        // "java.lang.Integer" sorts before "java.lang.Number", so Integer wins no matter what order
        // getDeclaredMethods returns
        assertEquals(Integer.class, getter.setterParamType.sourceType);
    }

    // ---------------- Omitting extends for primitive supertypes ----------------

    /** A Number subclass: TypeConverter maps the supertype to number, so the extends clause must be omitted. */
    public abstract static class MyNumber extends Number {
        private static final long serialVersionUID = 1L;
    }

    @Test
    void superClassMappingToTsPrimitiveOmitsExtends() {
        TypeDecl decl = new TypeReflector().reflect(MyNumber.class);
        String out = render(decl);
        assertTrue(out.contains("export class $TypeScriptClassRendererTest$MyNumber {"),
                "extends clause must be omitted entirely:\n" + out);
        assertFalse(out.contains("extends number"), "TS primitives cannot be heritage:\n" + out);
    }

    @Test
    void nonPrimitiveSuperClassStillRendersExtends() {
        TypeDecl decl = new TypeReflector().reflect(java.util.ArrayList.class);
        String out = render(decl);
        // Preserve superclass arguments instead of reducing ArrayList's parent to raw AbstractList.
        assertTrue(out.contains("export class $ArrayList<E> extends $AbstractList<E> implements"),
                "regular super class must keep its extends clause:\n" + out);
    }

    // ---------------- Heritage validity guard (values injected by probe.assign_type and changeSuper) ----------------

    /**
     * Builds a heritage slot that a probe has edited: {@code sourceType=null} plus
     * {@code overridden=true} makes renderSlot call renderTypeRef(ref). This matches what
     * {@code ProbeModifyTypeEventJS.override} (changeSuper) and
     * {@code ProbeAssignTypeEventJS.applySlot} (assign_type rewriting a SYMBOL slot) produce.
     */
    private static TypeSlot editedSlot(ApiTypeRef ref) {
        TypeSlot s = new TypeSlot(null, ref);
        s.overridden = true;
        return s;
    }

    private static ApiTypeRef symbol(String fqn) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", fqn));
    }

    /** Builds a synthetic TypeDecl directly: renameTo supplies the rendered name and sourceClass=null
     * marks it as purely synthetic. */
    private static TypeDecl decl(TypeDecl.Kind kind, String name) {
        TypeDecl d = new TypeDecl(kind, null, "test." + name);
        d.renameTo = name;
        return d;
    }

    /** The probe.assign("java.lang.CharSequence", "string") case: an implements entry assigned a
     * primitive type is omitted, one entry at a time. */
    @Test
    void implementsEntryAssignedToPrimitiveIsOmitted() {
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "StringBuilder");
        d.interfaces.add(editedSlot(symbol("java.lang.Appendable")));
        d.interfaces.add(editedSlot(ApiTypeRef.primitive("string")));   // The original $CharSequence has been assigned string

        String out = render(d);
        assertEquals("    export class $StringBuilder implements $Appendable {\n    }\n", out,
                "primitive implements entry must be omitted, valid entry kept");
    }

    /** When no implements entry is safe (primitive or union), the whole implements clause is omitted
     * and the declaration stays valid. */
    @Test
    void implementsClauseOmittedWhenAllEntriesUnsafe() {
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "Unsafe");
        d.interfaces.add(editedSlot(ApiTypeRef.primitive("number")));
        d.interfaces.add(editedSlot(ApiTypeRef.union(List.of(symbol("a.A"), symbol("b.B")))));

        String out = render(d);
        assertEquals("    export class $Unsafe {\n    }\n", out,
                "entire implements clause must be omitted when no entry is heritage-safe");
    }

    /** A union (event.union) or callback injected into implements: text containing | or => cannot be used as heritage. */
    @Test
    void unionAndCallbackInImplementsAreOmitted() {
        ApiTypeRef callback = ApiTypeRef.callback(ApiSignature.function(List.of(), ApiTypeRef.voidType()));
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "Mixed");
        d.interfaces.add(editedSlot(symbol("java.io.Serializable")));
        d.interfaces.add(editedSlot(ApiTypeRef.union(List.of(symbol("a.A"), symbol("b.B")))));
        d.interfaces.add(editedSlot(callback));

        String out = render(d);
        assertTrue(out.contains("export class $Mixed implements $Serializable {"),
                "valid implements entry must survive:\n" + out);
        assertFalse(out.contains("|"), "union cannot appear in heritage:\n" + out);
        assertFalse(out.contains("=>"), "callback cannot appear in heritage:\n" + out);
    }

    /** The same path for an interface extends list: an entry assigned a primitive type is omitted and
     * valid entries stay. */
    @Test
    void interfaceExtendsEntryAssignedToPrimitiveIsOmitted() {
        TypeDecl d = decl(TypeDecl.Kind.INTERFACE, "CharSequence");
        d.interfaces.add(editedSlot(symbol("java.lang.Comparable")));
        d.interfaces.add(editedSlot(ApiTypeRef.primitive("string")));

        String out = render(d);
        assertEquals("    export interface $CharSequence extends $Comparable {\n    }\n", out,
                "primitive extends entry must be omitted, valid entry kept");
    }

    /** Regression pin: valid SYMBOL heritage entries (extends and implements) render verbatim. */
    @Test
    void validSymbolHeritageEntriesAreKept() {
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "Pin");
        d.superType = editedSlot(symbol("java.util.AbstractList"));
        d.interfaces.add(editedSlot(symbol("java.util.List")));
        d.interfaces.add(editedSlot(symbol("java.io.Serializable")));

        String out = render(d);
        assertTrue(out.contains("export class $Pin extends $AbstractList implements $List, $Serializable {"),
                "symbol heritage entries must render verbatim:\n" + out);
    }

    /** changeSuper(event.union("a","b")): a union supertype drops the whole extends clause, and
     * implements stays as usual. */
    @Test
    void unionSuperTypeViaChangeSuperOmitsExtends() {
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "UnionSuper");
        d.superType = editedSlot(ApiTypeRef.union(List.of(symbol("a.A"), symbol("b.B"))));
        d.interfaces.add(editedSlot(symbol("java.io.Serializable")));

        String out = render(d);
        assertTrue(out.contains("export class $UnionSuper implements $Serializable {"),
                "extends must be omitted entirely for union super, implements kept:\n" + out);
        assertFalse(out.contains("extends"), "no extends clause may appear:\n" + out);
    }

    /** changeSuper injecting a callback ((...args: any[]) => any) also drops the whole extends clause. */
    @Test
    void callbackSuperTypeViaChangeSuperOmitsExtends() {
        ApiTypeRef callback = ApiTypeRef.callback(ApiSignature.function(List.of(), ApiTypeRef.voidType()));
        TypeDecl d = decl(TypeDecl.Kind.CLASS, "CallbackSuper");
        d.superType = editedSlot(callback);

        String out = render(d);
        assertEquals("    export class $CallbackSuper {\n    }\n", out,
                "extends clause must be omitted for callback super");
    }

    /** Identifier-escaping sample: a getter property starting with a digit, a TypeScript reserved word
     * as a parameter name, and generic array arguments (wildcard arrays). */
    public static class Pair<T, U> {}
    public static class MemoryModuleType<T> {}

    public static class IdentifierSample {
        public java.util.Date get2DigitYearStart() { return null; }
        public void apply(String function, int class_) {}
        public void addPair(Pair<MemoryModuleType<?>[], MemoryModuleType<?>[]> pair) {}
    }

    public static class VarargsSample {
        public void addPair(Pair<MemoryModuleType<?>[], MemoryModuleType<?>[]> pair, MemoryModuleType<?>... rest) {}
    }

    @Test
    void numericLeadingGetterPropertyFallsBackToMethodName() {
        // get2DigitYearStart maps to the property "2DigitYearStart", which starts with a digit and is not a
        // legal TypeScript getter name: the getter part is skipped and the plain method is declared, so
        // scripts can still call get2DigitYearStart()
        TypeDecl d = new TypeReflector().reflect(IdentifierSample.class);
        String out = render(d);
        assertFalse(out.contains("get 2DigitYearStart"), "numeric-leading getter must be skipped:\n" + out);
        assertTrue(out.contains("get2DigitYearStart(): $Date"), "method fallback must remain:\n" + out);
    }

    @Test
    void reservedWordParameterNamesAreEscaped() {
        TypeDecl d = new TypeReflector().reflect(IdentifierSample.class);
        String out = render(d);
        assertTrue(out.contains("apply(function_: string, class_: number): void"),
                "reserved-word params must be escaped with _ suffix:\n" + out);
    }

    @Test
    void genericArrayArgumentsRenderWithoutParameterLeak() {
        // Regression: the TypeScript rendering of Pair<MemoryModuleType<?>[], MemoryModuleType<?>[]> must be
        // complete and must not pull the following parameter name into a type
        TypeDecl d = new TypeReflector().reflect(IdentifierSample.class);
        String out = render(d);
        // The nested class name prefix is not hardcoded: each wildcard array argument is checked in place,
        // including the separating comma and the closing bracket, with no parameter name leaking
        assertTrue(out.contains("<any>[], "), "first wildcard array arg missing:\n" + out);
        assertTrue(out.replaceAll("\\s+", "").contains("MemoryModuleType<any>[]>):void"), "second wildcard array arg missing:\n" + out);
    }

    @Test
    void varargsAfterGenericArrayArgsDoesNotLeakParameterName() {
        // Reproduces the shape of Brain.addActivityAndRemoveMemoriesWhenStopped: generic array arguments
        // followed by varargs. Varargs must keep the generic argument <any>; rendering
        // MemoryModuleType<?>... as a raw type drops it
        TypeDecl d = new TypeReflector().reflect(VarargsSample.class);
        String out = render(d);
        // The nested class name prefix is not hardcoded: checks the argument chain and the varargs, including
        // the generic argument <any> instead of a raw type, plus the separator after the first array
        // argument closes
        assertTrue(out.contains("<any>[], "), "generic pair args incomplete:\n" + out);
        assertFalse(out.contains(", rest:"), "varargs param name must not leak into generic args:\n" + out);
        assertTrue(out.replaceAll("\\s+", "").contains("MemoryModuleType<any>[]):void"),
                "varargs param must keep generic args and render as optional array:\n" + out);
    }

    // ---------------- @Overload / @DeprecatedNekojs (annotation to IR to rendering) ----------------

    /** Sample with hand-written overloads and deprecation marks: a method with two overloads, one with
     * a doc and returns, one that inherits its return type. */
    public static class OverloadDeprecatedSample {
        @com.tkisor.nekojs.api.annotation.Doc("Creates a stack.")
        @com.tkisor.nekojs.api.annotation.Overload({"id: string"})
        @com.tkisor.nekojs.api.annotation.Overload(
                value = {"item: $ItemStack", "count?: number"},
                returns = "string",
                doc = "Copy with a count.")
        public static java.util.List<String> of(java.util.function.Supplier<?> input, int flags) {
            return java.util.List.of();
        }

        @com.tkisor.nekojs.api.annotation.DeprecatedNekojs(
                value = "Moved to matchTag.",
                replacedBy = "matchTag")
        public void anyTag(String tag) { }

        @com.tkisor.nekojs.api.annotation.DeprecatedNekojs(replacedBy = "ACTIVE_FLAGS")
        public static final int LEGACY_FLAG = 1;

        @com.tkisor.nekojs.api.annotation.Overload({"count?: number"})
        public OverloadDeprecatedSample(String id, int extra) { }
    }

    @Test
    void overloadsRenderAsAdditionalDeclarationsAfterPrimarySignature() {
        TypeDecl d = new TypeReflector().reflect(OverloadDeprecatedSample.class);
        String out = render(d);

        // The main declaration is still there (the reflected signature)
        assertTrue(out.contains("static of(input: $Supplier<any>, flags: number): $List<string>;"), out);
        // Overload 1: no returns, so it renders with the return type of the main declaration
        assertTrue(out.contains("static of(id: string): $List<string>;"), out);
        // Overload 2: has a doc (collapsed into one-line JSDoc) and an explicit returns
        assertTrue(out.contains("/** Copy with a count. */"), out);
        assertTrue(out.contains("static of(item: $ItemStack, count?: number): string;"), out);
        // Declaration order: the main signature first, then the overloads in annotation declaration order
        assertTrue(out.indexOf("of(input: $Supplier<any>") < out.indexOf("of(id: string)"), out);
        assertTrue(out.indexOf("of(id: string)") < out.indexOf("of(item: $ItemStack"), out);
    }

    @Test
    void constructorOverloadsRenderWithoutReturnType() {
        TypeDecl d = new TypeReflector().reflect(OverloadDeprecatedSample.class);
        String out = render(d);
        assertTrue(out.contains("constructor(id: string, extra: number);"), out);
        assertTrue(out.contains("constructor(count?: number);"), out);
    }

    @Test
    void deprecatedMembersRenderJsDocTag() {
        TypeDecl d = new TypeReflector().reflect(OverloadDeprecatedSample.class);
        String out = render(d);

        // Method: reason plus replacement
        assertTrue(out.contains("@deprecated Moved to matchTag. Use matchTag instead."), out);
        // Field: replacement only
        assertTrue(out.contains("@deprecated Use ACTIVE_FLAGS instead."), out);
        // JSDoc block shape: a single doc entry collapses to one line
        assertTrue(out.contains("/** @deprecated Use ACTIVE_FLAGS instead. */"), out);
    }

    @Test
    void unannotatedMembersRenderNoDeprecationOrOverloadLines() {
        // Guards the regression: unannotated members render with no change, which is what keeps the
        // golden files byte-identical
        TypeDecl d = new TypeReflector().reflect(IdentifierSample.class);
        String out = render(d);
        assertFalse(out.contains("@deprecated"), out);
        // This sample has no @Overload, so the method name must appear only once (a single declaration)
        long applyCount = out.split("apply", -1).length - 1;
        assertEquals(1, applyCount, "unannotated method must render exactly one declaration");
    }

    private static String render(TypeDecl decl) {
        return new TypeScriptClassRenderer(new TypeAliasRegistry()).render(decl);
    }
}

