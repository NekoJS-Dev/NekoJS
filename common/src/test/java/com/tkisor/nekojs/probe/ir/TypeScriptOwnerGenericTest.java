package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptOwnerGenericTest {
    public interface CapturedContract<X> { default <T> X choose(T input) { return null; } }

    public static class Owner<T extends Owner<T>> {
        public class Inner<V> implements CapturedContract<T> {
            public V value;
            public Inner(V value) { this.value = value; }
            public T update(V value) { this.value = value; return null; }
            public class Leaf<W> {
                public V middle;
                public W leaf;
                public T owner() { return null; }
            }
        }

        public static class Boundary<K> {
            public class Child<V> { public K key; public V value; }
        }
    }

    public static class Shadow<T> {
        public class Member<T> {
            public T value;
            public class Leaf<T1> { public T member; public T1 leaf; }
        }
    }

    public static class Concrete extends Owner<Concrete> {
        public int count;
        public Owner<Concrete>.Inner<String> text;
        public Owner<Concrete>.Inner<Integer> number;
        public Owner.Inner raw;
        public Owner<Concrete>.Inner<String>.Leaf<Boolean> leaf;
        public Owner.Boundary<Integer>.Child<String> boundary;
        public Shadow<Integer>.Member<String>.Leaf<Boolean> shadow;
        public void consume(Owner<Concrete>.Inner<String> value) {}
        public void factory(InnerFactory factory) {}
        public void sink(InnerSink sink) {}
    }

    public interface InnerFactory { Owner<Concrete>.Inner<String> make(); }
    public interface InnerSink { void accept(Owner<Concrete>.Inner<String> value); }

    public static class Tail {}
    public static class Bound { public Tail tail; }
    public static class BoundOwner<T extends Bound> {
        public class Inner<V> { public T captured; }
    }
    public static class OwnerRoot { public BoundOwner<Bound>.Inner<String> value; }

    @Test
    void primaryScanTraversesActualOwnerArgumentsAndBoundsWithinItsExistingLimits() {
        for (Class<?> root : List.of(OwnerRoot.class, BoundOwner.Inner.class)) {
            var snapshot = new NekoScriptCatalogSnapshot(List.of(),
                    List.of(BindingCatalogEntry.of("Root", ScriptType.SERVER, root, false)), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, Map.of(), List.of());
            var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                    List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
            assertTrue(ProbeCoordinator.collectClasses(snapshot, config).contains(Bound.class));
            assertFalse(ProbeCoordinator.collectClasses(snapshot, config).contains(Tail.class));
            var excluded = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                    List.of(getClass().getPackageName()), List.of(),
                    List.of("re:" + java.util.regex.Pattern.quote(Bound.class.getName()) + "$"), List.of(), 1, "SMART"));
            assertFalse(ProbeCoordinator.collectClasses(snapshot, excluded).contains(Bound.class));
            var deeper = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                    List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 2, "SMART"));
            assertTrue(ProbeCoordinator.collectClasses(snapshot, deeper).contains(Tail.class));
        }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void nonStaticInnerTypesRetainTheirCapturedOwnerArguments() throws Exception {
        Path output = Path.of("build/probe-ts-owner-generics/plain");
        List<Class<?>> types = List.of(Owner.class, Owner.Inner.class, Owner.Inner.Leaf.class, Concrete.class,
                Owner.Boundary.class, Owner.Boundary.Child.class, Shadow.class, Shadow.Member.class, Shadow.Member.Leaf.class);
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of());
        var files = new TypeScriptProbeBackend().render(context);
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptOwnerGenericTest$Concrete as Concrete } from 'java:com/tkisor/nekojs/probe/ir';
                declare const concrete: Concrete;
                const updated: Concrete = concrete.text.update('value');
                const chosen: Concrete = concrete.text.choose<number>(12);
                // @ts-expect-error An inherited method's variable must not replace its captured result variable.
                const wrongChosen: string = concrete.text.choose<number>(12);
                // @ts-expect-error The inherited method retains its own explicit type parameter.
                concrete.text.choose<string>(12);
                const count: number = updated.count;
                const text: string = concrete.text.value;
                concrete.consume(concrete.text);
                concrete.factory(() => concrete.text);
                concrete.sink(value => { const owner: Concrete = value.update('value'); });
                concrete.raw.update(12);
                const leafOwner: Concrete = concrete.leaf.owner();
                const middle: string = concrete.leaf.middle;
                const leaf: boolean = concrete.leaf.leaf;
                const boundaryKey: number = concrete.boundary.key;
                const boundaryValue: string = concrete.boundary.value;
                const shadowMember: string = concrete.shadow.member;
                const shadowLeaf: boolean = concrete.shadow.leaf;
                // @ts-expect-error Three levels retain each argument's identity.
                const wrongLeaf: string = concrete.leaf.leaf;
                // @ts-expect-error Same-spelled Java variables belong to different declarations.
                const wrongShadow: number = concrete.shadow.member;
                // @ts-expect-error Captured owners retain their actual result contract.
                const wrong: string = concrete.text.update('value');
                // @ts-expect-error The inner argument keeps its own identity.
                concrete.text.update(12);
                // @ts-expect-error Parameterized inner input types preserve their owner and member arguments.
                concrete.consume(concrete.number);
                // @ts-expect-error Callback results retain captured and member arguments.
                concrete.factory(() => concrete.number);
                """);
        String declaration = files.get("@package/com/tkisor/nekojs/probe/ir/index.d.ts");
        assertTrue(declaration.contains("class $TypeScriptOwnerGenericTest$Owner$Inner<T extends $TypeScriptOwnerGenericTest$Owner<T>, V>"), declaration);
        assertTrue(declaration.contains("text: $TypeScriptOwnerGenericTest$Owner$Inner<$TypeScriptOwnerGenericTest$Concrete, string>"), declaration);
        var pythonContext = new ProbeContext.Of(snapshot, types, config, context.paths(), "python", output.resolve("python"),
                types.stream().map(type -> new TypeReflector().reflect(type)).toList());
        String python = new PythonProbeBackend().render(pythonContext)
                .get("nekojs/_java/com/tkisor/nekojs/probe/ir/__init__.pyi");
        assertTrue(python.contains("def update(self, value: Any) -> Any: ..."), python);
        assertFalse(python.contains("-> T:"), python);
    }

    @Test
    void editedInnerSlotsAndAdapterAliasesUseTheSameCapturedArity() throws Exception {
        Path output = Path.of("build/probe-ts-owner-generics/edited");
        List<Class<?>> types = List.of(Owner.class, Owner.Inner.class, Concrete.class);
        var declarations = types.stream().map(type -> new TypeReflector().reflect(type)).toList();
        var inner = declarations.stream().filter(type -> type.sourceClass == Owner.Inner.class).findFirst().orElseThrow();
        inner.renameTo = "RenamedInner";
        inner.mutated = true;
        var update = inner.methods.stream().filter(method -> method.name.equals("update")).findFirst().orElseThrow();
        update.returnType.ref = ApiTypeRef.primitive("boolean");
        update.returnType.overridden = true;
        var value = inner.fields.stream().filter(field -> field.name.equals("value")).findFirst().orElseThrow();
        value.type.ref = ApiTypeRef.primitive("string");
        value.type.overridden = true;
        var adapter = new AdapterCatalogEntry(Owner.Inner.class,
                List.of(AdapterInputShape.self(), AdapterInputShape.literal("inner")),
                ConversionPrecedence.HIGH, Optional.empty());
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(adapter),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output),
                "typescript", output, declarations);
        var files = new TypeScriptProbeBackend().render(context);
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptOwnerGenericTest$Concrete as Concrete,
                    $TypeScriptOwnerGenericTest$Owner$Inner_ as Input } from 'java:com/tkisor/nekojs/probe/ir';
                declare const concrete: Concrete;
                const updated: boolean = concrete.text.update('value');
                const text: string = concrete.number.value;
                const input: Input<Concrete, string> = concrete.text;
                concrete.consume(input);
                concrete.consume('inner');
                // @ts-expect-error Explicit return edits must not be rebuilt from the owner signature.
                const wrong: Concrete = concrete.text.update('value');
                // @ts-expect-error Explicit field edits win over reflected member variables.
                const wrongField: number = concrete.number.value;
                // @ts-expect-error The adapter keeps the explicit inner argument.
                const wrongInput: Input<Concrete, string> = concrete.number;
                """);
        String declaration = files.get("@package/com/tkisor/nekojs/probe/ir/index.d.ts");
        assertTrue(declaration.contains("export type $TypeScriptOwnerGenericTest$Owner$Inner<T extends $TypeScriptOwnerGenericTest$Owner<T>, V> = $RenamedInner<T, V>;"), declaration);
        assertTrue(declaration.contains("$TypeScriptOwnerGenericTest$Owner$Inner_<T extends $TypeScriptOwnerGenericTest$Owner<T> = any, V = any>"), declaration);
    }

    @Test
    void rawInnerEventsAndDispatchInputsUseTheCapturedArity() throws Exception {
        Path output = Path.of("build/probe-ts-owner-generics/events");
        var event = EventCatalogEntry.of("OwnerEvents", "visit", ScriptType.SERVER,
                Owner.Inner.class, Owner.Inner.class, false, true);
        var manual = ManualDeclarationCatalogEntry.of("test:owner", "declare function ownedAccept(value: OwnerInput): OwnerOutput;",
                "", List.of()).withReferences(Map.of(
                        "OwnerInput", ManualDeclarationCatalogEntry.TypeReference.input(Owner.Inner.class),
                        "OwnerOutput", ManualDeclarationCatalogEntry.TypeReference.host(Owner.Inner.class)));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(event), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(manual), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of());
        var files = new TypeScriptProbeBackend().render(context);
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@manual/**/*.d.ts","@side-only/server/events/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptOwnerGenericTest$Owner$Inner as Inner } from 'java:com/tkisor/nekojs/probe/ir';
                declare const inner: Inner<any, string>;
                const returned: OwnerOutput = ownedAccept(inner);
                returned.update('value');
                // @ts-expect-error Explicit raw manual references retain the host member shape.
                returned.missing;
                // @ts-expect-error Raw manual inputs do not accept scalar values.
                ownedAccept(12);
                OwnerEvents.visit(inner, event => {
                    event.update('value');
                    // @ts-expect-error A raw inner event still retains its actual member names.
                    event.missing;
                });
                // @ts-expect-error Raw dispatch erasure preserves the host shape.
                OwnerEvents.visit(12, event => {});
                """);
        String events = files.get("@side-only/server/events/index.d.ts");
        assertTrue(events.contains("extra: $TypeScriptOwnerGenericTest$Owner$Inner<any, any>"), events);
    }
}
