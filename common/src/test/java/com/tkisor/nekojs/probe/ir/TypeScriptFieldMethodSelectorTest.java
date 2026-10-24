package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.Overload;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptFieldMethodSelectorTest {
    public interface Position { double x(); }
    public static class Coordinate implements Position {
        public double x = 3;
        public double x() { return x + 1; }
        public Coordinate x(double value) { x = value; return this; }
    }
    public static class OrdinaryPosition implements Position {
        public double x() { return 5; }
    }
    public static class OrdinaryGrandchild extends OrdinaryPosition {}
    public interface InheritedPosition extends Position {}
    public interface AdditionalPosition { double x(); }
    public static class InheritedField extends Coordinate implements AdditionalPosition {}
    public static class AdditionalOrdinary implements AdditionalPosition { public double x() { return 6; } }
    public static class StaticField { public static double x; public double x() { return 1; } }
    public static class GetterField { public double getValue; public double getValue() { return 1; } }
    public static class UnrelatedField { public int value; }
    public static class FieldOnlyParent { public int x = 1; }
    public static class MethodOnlyChild extends FieldOnlyParent { public int x() { return 2; } }
    public static class RenameParent {
        public String x() { return "parent"; }
        public String x(int input) { return "parent"; }
    }
    public static class RenameChild extends RenameParent {
        public double y;
        public String x(double input) { return "child"; }
    }
    public static class OrderedMethods {
        public int first(int input) { return input; }
        public void middle() {}
        @Remap("first") public String zLast(String input) { return input; }
    }
    public interface GenericOperation<T> { T value(T input); }
    public static class StringField implements GenericOperation<String> {
        public double value = 9;
        public String value(String input) { return input; }
    }
    public static class Payload<T> { public T item; }
    public interface GenericPosition<P> { P value(); }
    public static class FieldPosition<P> implements GenericPosition<P> {
        public P value;
        public P value() { return value; }
    }
    public interface InheritedGeneric<C> extends GenericPosition<Payload<C>> {}
    public interface BoundedPosition<P> { <C extends Payload<P>> C value(C input); }
    public static class BoundedField<P> implements BoundedPosition<P> {
        public P value;
        public <C extends Payload<P>> C value(C input) { return input; }
    }
    public interface InheritedBounded<C> extends BoundedPosition<C> {}
    public interface AuthoredPosition<P> {
        @Overload(value = "input: P", returns = "P") P value(P input);
    }
    public static class AuthoredField<P> implements AuthoredPosition<P> {
        public P value;
        public P value(P input) { return input; }
    }
    public interface InheritedAuthored<C> extends AuthoredPosition<Payload<C>> {}
    public interface OverloadedInheritedAuthored<C> extends AuthoredPosition<Payload<C>> {
        Payload<C> value(Payload<C> input, int marker);
    }
    public interface DefaultPosition { default double x() { return 4; } }
    public static class DefaultField implements DefaultPosition { public double x; }
    public static class RemappedField {
        public double value;
        @Remap("value") public double operation() { return value; }
    }
    public interface EnumPosition { double x(); }
    public enum EnumField implements EnumPosition {
        VALUE;
        public double x;
        public double x() { return x; }
    }

    @BeforeAll
    static void initializePlatform() { TestPlatformInit.ensureInitialized(); }

    @Test
    void fieldAndMethodSelectorsKeepTheParentContractAndOrdinarySafeBranches() throws Exception {
        Path output = generate("plain", List.of(Coordinate.class, Position.class,
                OrdinaryPosition.class, InheritedPosition.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$Coordinate as Coordinate,
                    $TypeScriptFieldMethodSelectorTest$Position as Position,
                    $TypeScriptFieldMethodSelectorTest$OrdinaryPosition as Ordinary,
                    $TypeScriptFieldMethodSelectorTest$InheritedPosition as Inherited } from 'java:com/tkisor/nekojs/probe/ir';
                declare const coordinate: Coordinate;
                declare const ordinary: Ordinary;
                declare const inherited: Inherited;
                const parent: Position = coordinate;
                const read: number = coordinate.x;
                coordinate.x = 7;
                const called: number = coordinate["x()"]();
                const updated: Coordinate = coordinate["x(double)"](8);
                const extracted = coordinate["x()"];
                const extractedValue: number = extracted();
                const parentValue: number = parent["x()"]();
                const ordinaryValue: number = ordinary.x();
                const ordinarySelector: number = ordinary["x()"]();
                const inheritedValue: number = inherited.x();
                const inheritedSelector: number = inherited["x()"]();
                // @ts-expect-error The actual field is not a callable property value.
                coordinate.x();
                // @ts-expect-error The parent uses the selector to admit its field-bearing implementation.
                parent.x();
                // @ts-expect-error The selected Java setter retains its numeric input.
                coordinate["x(double)"]('bad');
                // @ts-expect-error Field writes keep their original numeric type.
                coordinate.x = 'bad';
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("\"x()\"(): number;"), declaration);
        assertTrue(declaration.contains("\"x(double)\"("), declaration);
    }

    @Test
    void anInheritedOrdinaryOperationRetainsItsActualGenericArguments() throws Exception {
        Path output = generate("generic", List.of(Payload.class, GenericPosition.class,
                FieldPosition.class, InheritedGeneric.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$Payload as Payload,
                    $TypeScriptFieldMethodSelectorTest$GenericPosition as Generic,
                    $TypeScriptFieldMethodSelectorTest$FieldPosition as Field,
                    $TypeScriptFieldMethodSelectorTest$InheritedGeneric as Inherited } from 'java:com/tkisor/nekojs/probe/ir';
                declare const field: Field<Payload<string>>;
                declare const inherited: Inherited<number>;
                const parent: Generic<Payload<string>> = field;
                const fieldValue: Payload<string> = field.value;
                const called: Payload<string> = field["value()"]();
                const ordinary: Payload<number> = inherited.value();
                const selector: Payload<number> = inherited["value()"]();
                // @ts-expect-error Materialization substitutes the actual parent argument.
                const wrong: Payload<string> = inherited.value();
                // @ts-expect-error The generic field keeps its own argument.
                field.value = ordinary;
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("value(): $TypeScriptFieldMethodSelectorTest$Payload<C>;"), declaration);
    }

    private static Path generate(String name, List<Class<?>> types) throws Exception {
        return generate(name, types, List.of(), new TypeScriptProbeBackend());
    }

    private static Path generate(String name, List<Class<?>> types, List<TypeDecl> declarations,
                                 TypeScriptProbeBackend backend) throws Exception {
        Path output = Path.of("build/probe-ts-field-method-selectors", name);
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(TypeScriptFieldMethodSelectorTest.class.getPackageName()),
                List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output),
                "typescript", output, declarations);
        var modules = new java.util.ArrayList<String>();
        for (var entry : backend.render(context).entrySet()) {
            if (!entry.getKey().startsWith("@package/") || !entry.getKey().endsWith(".d.ts")) continue;
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
            modules.add(entry.getKey());
        }
        modules.sort(String::compareTo);
        modules.add("exercise.ts");
        Files.writeString(output.resolve("tsconfig.json"), new com.google.gson.Gson().toJson(Map.of(
                "compilerOptions", Map.of("strict", true, "noEmit", true, "skipLibCheck", false,
                        "target", "ES2022", "module", "ESNext", "moduleResolution", "Bundler", "types", List.of(),
                        "baseUrl", ".", "paths", Map.of("java:*", List.of("./@package/*"))), "files", modules)));
        return output;
    }

    @Test
    void inheritedMethodVariablesRemainDistinctFromTheReceiversClassVariables() throws Exception {
        Path output = generate("method-scope", List.of(Payload.class, BoundedPosition.class,
                BoundedField.class, InheritedBounded.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$Payload as Payload,
                    $TypeScriptFieldMethodSelectorTest$InheritedBounded as Inherited,
                    $TypeScriptFieldMethodSelectorTest$BoundedField as Field } from 'java:com/tkisor/nekojs/probe/ir';
                declare const inherited: Inherited<number>;
                declare const field: Field<number>;
                declare const input: Payload<number>;
                const ordinary: Payload<number> = inherited.value(input);
                const selector: Payload<number> = field["value(com.tkisor.nekojs.probe.ir.TypeScriptFieldMethodSelectorTest$Payload)"](input);
                // @ts-expect-error The substituted method bound still requires the class's actual argument.
                inherited.value({item: 'wrong'});
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("value<C1 extends $TypeScriptFieldMethodSelectorTest$Payload<C>>"), declaration);
    }

    @Test
    void hiddenAncestorEditsSuppressUneditedInheritedCopiesAndResetBetweenRenders() throws Exception {
        var parent = new TypeReflector().reflect(DefaultPosition.class);
        var child = new TypeReflector().reflect(DefaultField.class);
        var backend = new TypeScriptProbeBackend();
        parent.methods.stream().filter(method -> method.name.equals("x")).forEach(method -> method.hidden = true);
        parent.mutated = true;
        Path hidden = generate("hidden", List.of(DefaultField.class), List.of(parent, child), backend);
        String hiddenDeclaration = Files.readString(hidden.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertFalse(hiddenDeclaration.contains("\"x()\""), hiddenDeclaration);
        assertFalse(hiddenDeclaration.contains("        x()"), hiddenDeclaration);
        Files.writeString(hidden.resolve("exercise.ts"), "export {};\n");
        Path reused = generate("reused", List.of(DefaultField.class), List.of(), backend);
        String reusedDeclaration = Files.readString(reused.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(reusedDeclaration.contains("\"x()\"(): number;"), reusedDeclaration);
        Files.writeString(reused.resolve("exercise.ts"), "export {};\n");
    }

    @Test
    void selectorsUseOriginalJavaNames() throws Exception {
        Path output = generate("names", List.of(RemappedField.class));
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("\"operation()\"(): number;"), declaration);
        assertFalse(declaration.contains("\"value()\""), declaration);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$RemappedField as Field } from 'java:com/tkisor/nekojs/probe/ir';
                declare const field: Field;
                const value: number = field.value;
                const called: number = field["operation()"]();
                // @ts-expect-error A remapped ordinary name is not the Java selector identity.
                field["value()"]();
                """);
    }

    @Test
    void enumsDoNotSeedConflictsForInstanceMembersTheirRendererDoesNotEmit() throws Exception {
        Path output = generate("enum-structure", List.of(EnumField.class, EnumPosition.class));
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertFalse(declaration.contains("\"x()\""), declaration);
    }

    @Test
    void anInheritedFieldAlsoConstrainsInterfacesAddedByTheSubclass() throws Exception {
        Path output = generate("inherited-field", List.of(InheritedField.class, AdditionalOrdinary.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$InheritedField as Field,
                    $TypeScriptFieldMethodSelectorTest$AdditionalPosition as Position,
                    $TypeScriptFieldMethodSelectorTest$AdditionalOrdinary as Ordinary } from 'java:com/tkisor/nekojs/probe/ir';
                declare const field: Field;
                declare const ordinary: Ordinary;
                const parent: Position = field;
                const value: number = field.x;
                const called: number = parent["x()"]();
                const safe: number = ordinary.x();
                const safeSelector: number = ordinary["x()"]();
                // @ts-expect-error The newly implemented interface admits a field-bearing instance.
                parent.x();
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("\"x()\"(): number;"), declaration);
    }

    @Test
    void materializedOrdinaryCallsRetainEditedReturnTypesWithoutMutatingTheSharedIR() throws Exception {
        var parent = new TypeReflector().reflect(GenericPosition.class);
        var field = new TypeReflector().reflect(FieldPosition.class);
        var inherited = new TypeReflector().reflect(InheritedGeneric.class);
        var edits = new ProbeModifyTypeEventJS(Map.of(parent.fqn, parent, field.fqn, field));
        edits.forClass(parent.fqn).changeReturnType("value", "boolean");
        edits.forClass(field.fqn).changeReturnType("value", "boolean");
        List<String> parentBefore = methodFingerprints(parent);
        List<String> fieldBefore = methodFingerprints(field);
        List<String> inheritedBefore = methodFingerprints(inherited);
        Path output = generate("edited", List.of(FieldPosition.class, InheritedGeneric.class),
                List.of(parent, field, inherited), new TypeScriptProbeBackend());
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$InheritedGeneric as Inherited,
                    $TypeScriptFieldMethodSelectorTest$FieldPosition as Field } from 'java:com/tkisor/nekojs/probe/ir';
                declare const inherited: Inherited<number>;
                declare const field: Field<number>;
                const ordinary: boolean = inherited.value();
                const selected: boolean = field["value()"]();
                const data: number = field.value;
                // @ts-expect-error An explicit ancestor return edit remains authoritative.
                const wrong: number = inherited.value();
                """);
        assertEquals(parentBefore, methodFingerprints(parent), "render must not mutate the shared parent IR");
        assertEquals(fieldBefore, methodFingerprints(field), "render must not mutate the shared field IR");
        assertEquals(inheritedBefore, methodFingerprints(inherited), "render must not mutate the shared inherited IR");
    }

    /** Captures the mutable method state the shared IR exposes, so snapshots detect in-place edits. */
    private static List<String> methodFingerprints(TypeDecl declaration) {
        return declaration.methods.stream()
                .map(method -> method.effectiveName() + ":" + method.returnType + ":" + method.params.size()
                        + ":" + method.hidden + ":" + method.explicitlyEdited)
                .toList();
    }

    @Test
    void staticAndSyntheticFieldsDoNotClaimAJavaInstanceSelector() throws Exception {
        Path output = generate("static", List.of(StaticField.class));
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertFalse(declaration.contains("\"x()\""), declaration);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptFieldMethodSelectorTest$StaticField as Field } from 'java:com/tkisor/nekojs/probe/ir';
                declare const field: Field;
                const value: number = Field.x;
                const called: number = field.x();
                """);
        var synthetic = new TypeReflector().reflect(OrdinaryPosition.class);
        synthetic.fields.add(new FieldDecl("x", TypeSlot.of(null,
                com.tkisor.nekojs.api.surface.ApiTypeRef.primitive("number"))));
        var renderer = new TypeScriptClassRenderer(new com.tkisor.nekojs.probe.types.TypeAliasRegistry());
        renderer.setClassDeclarations(Map.of(synthetic.fqn, synthetic));
        assertFalse(renderer.render(synthetic).contains("\"x()\""));
    }

    @Test
    void concreteGenericImplementationsRetainTheirErasedParentSelectorContract() throws Exception {
        try (Context context = Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> name.equals(StringField.class.getName()))
                .allowCreateThread(false).allowCreateProcess(false).build()) {
            context.eval("js", "var field = new (Java.type('" + StringField.class.getName() + "'))();");
            assertEquals("value", context.eval("js", "field['value(java.lang.Object)']('value')").asString());
            assertEquals("value", context.eval("js", "field['value(java.lang.String)']('value')").asString());
        }
        Path output = generate("generic-override", List.of(GenericOperation.class, StringField.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$GenericOperation as Operation,
                    $TypeScriptFieldMethodSelectorTest$StringField as Field } from 'java:com/tkisor/nekojs/probe/ir';
                declare const field: Field;
                const parent: Operation<string> = field;
                const value: number = field.value;
                const direct: string = field["value(java.lang.String)"]('input');
                const inherited: string = field["value(java.lang.Object)"]('input');
                const selected: string = parent["value(java.lang.Object)"]('input');
                // @ts-expect-error Erasure does not remove the actual generic argument constraint.
                field["value(java.lang.Object)"](false);
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        String child = declaration.substring(declaration.indexOf("export class $TypeScriptFieldMethodSelectorTest$StringField"));
        assertTrue(child.contains("\"value(java.lang.Object)\""), child);
    }

    @Test
    void inheritedAuthoredOverloadsRemainInTheirOriginalGenericScope() throws Exception {
        Path output = generate("authored", List.of(AuthoredPosition.class, AuthoredField.class, InheritedAuthored.class,
                OverloadedInheritedAuthored.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$Payload as Payload,
                    $TypeScriptFieldMethodSelectorTest$InheritedAuthored as Inherited,
                    $TypeScriptFieldMethodSelectorTest$OverloadedInheritedAuthored as Overloaded } from 'java:com/tkisor/nekojs/probe/ir';
                declare const inherited: Inherited<number>;
                declare const overloaded: Overloaded<number>;
                declare const input: Payload<number>;
                const value: Payload<number> = inherited.value(input);
                const inheritedOverload: Payload<number> = overloaded.value(input);
                const ownOverload: Payload<number> = overloaded.value(input, 1);
                // @ts-expect-error An authored overload retains the substituted class argument.
                inherited.value({item: 'wrong'});
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        String inherited = declaration.substring(declaration.indexOf("export interface $TypeScriptFieldMethodSelectorTest$InheritedAuthored"));
        assertFalse(declaration.contains("value(input: P)"), inherited);
    }

    @Test
    void changingGetterProjectionRulesRefreshesTheSelectorSnapshot() {
        var declaration = new TypeReflector().reflect(GetterField.class);
        var renderer = new TypeScriptClassRenderer(new com.tkisor.nekojs.probe.types.TypeAliasRegistry());
        renderer.overrideGetter(GetterField.class, "value", "number", null);
        renderer.setClassDeclarations(Map.of(declaration.fqn, declaration));
        assertFalse(renderer.render(declaration).contains("\"getValue()\""));
        renderer.setBeanAliasExclusions(List.of(new com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry(
                GetterField.class, "getValue", "value")));
        assertTrue(renderer.render(declaration).contains("\"getValue()\"(): number;"));
        renderer.setBeanAliasExclusions(List.of());
        assertFalse(renderer.render(declaration).contains("\"getValue()\""));
    }

    @Test
    void anUnrelatedSameNameFieldDoesNotChangeAnOrdinaryInheritanceFamily() {
        var parent = new TypeReflector().reflect(AuthoredPosition.class);
        var child = new TypeReflector().reflect(OverloadedInheritedAuthored.class);
        var unrelated = new TypeReflector().reflect(UnrelatedField.class);
        var renderer = new TypeScriptClassRenderer(new com.tkisor.nekojs.probe.types.TypeAliasRegistry());
        String ordinary = renderer.render(child);
        renderer.setClassDeclarations(Map.of(parent.fqn, parent, child.fqn, child, unrelated.fqn, unrelated));
        assertEquals(ordinary, renderer.render(child));
    }

    @Test
    void aMethodIntroducedBelowAnInheritedFieldUsesItsRealSelector() throws Exception {
        try (Context context = Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("field", new MethodOnlyChild());
            assertEquals(1, context.eval("js", "field.x").asInt());
            assertEquals(2, context.eval("js", "field['x()']()").asInt());
        }
        Path output = generate("inherited-origin", List.of(MethodOnlyChild.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$FieldOnlyParent as Parent,
                    $TypeScriptFieldMethodSelectorTest$MethodOnlyChild as Child } from 'java:com/tkisor/nekojs/probe/ir';
                declare const child: Child;
                const parent: Parent = child;
                const value: number = child.x;
                const selected: number = child["x()"]();
                // @ts-expect-error The inherited field remains a number.
                child.x();
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(declaration.contains("\"x()\"(): number;"), declaration);
    }

    @Test
    void inheritedCopyNormalizationUsesTheEditedAncestorsEffectiveName() throws Exception {
        var parent = new TypeReflector().reflect(RenameParent.class);
        var child = new TypeReflector().reflect(RenameChild.class);
        new ProbeModifyTypeEventJS(Map.of(parent.fqn, parent)).forClass(parent.fqn).renameMethod("x", "y");
        Path output = generate("renamed-parent", List.of(RenameChild.class), List.of(parent, child), new TypeScriptProbeBackend());
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$RenameChild as Child } from 'java:com/tkisor/nekojs/probe/ir';
                declare const child: Child;
                const data: number = child.y;
                const parent: string = child["x()"]();
                const own: string = child.x(0.5);
                // @ts-expect-error The unedited inherited copy follows the ancestor's explicit rename.
                child.x();
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertFalse(declaration.contains("        x(): string;"), declaration);
    }

    @Test
    void safeDescendantsReuseTheOrdinaryOperationAlreadyKeptByTheirParent() throws Exception {
        Path output = generate("safe-descendant", List.of(Coordinate.class, OrdinaryGrandchild.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$OrdinaryGrandchild as Child } from 'java:com/tkisor/nekojs/probe/ir';
                declare const child: Child;
                const ordinary: number = child.x();
                const selector: number = child["x()"]();
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        String child = declaration.substring(declaration.indexOf("export class $TypeScriptFieldMethodSelectorTest$OrdinaryGrandchild"));
        child = child.substring(0, child.indexOf("    }"));
        assertFalse(child.contains("x()"), child);
    }

    @Test
    void ordinaryMethodsKeepTheirEditedIROrderWhenNoIndexedGroupIsNeeded() throws Exception {
        Path output = generate("method-order", List.of(OrderedMethods.class));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptFieldMethodSelectorTest$OrderedMethods as Methods } from 'java:com/tkisor/nekojs/probe/ir';
                declare const methods: Methods;
                const numeric: number = methods.first(1);
                const textual: string = methods.first('value');
                methods.middle();
                // @ts-expect-error Keeping declaration order does not widen either overload.
                methods.first(false);
                """);
        String declaration = Files.readString(output.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        int numeric = declaration.indexOf("first(input: number)");
        int middle = declaration.indexOf("middle()");
        int textual = declaration.indexOf("first(input: string)");
        assertTrue(numeric >= 0 && numeric < middle && middle < textual, declaration);
    }
}
