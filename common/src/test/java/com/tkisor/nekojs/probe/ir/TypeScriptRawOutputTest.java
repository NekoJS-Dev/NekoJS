package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptRawOutputTest {
    public static class Slot<T> {
        public T value;
        public Slot(T value) { this.value = value; }
        public T get() { return value; }
    }
    @SuppressWarnings("rawtypes")
    public static class RawBound<T extends Slot> { public T value; }
    @SuppressWarnings("rawtypes")
    public static class NestedBound<T extends Slot<Slot>> { public T value; }
    @SuppressWarnings("rawtypes")
    public static class RawComparable<T extends Comparable> {
        public T value;
        public static RawComparable<Integer> numbers() { return new RawComparable<>(); }
    }
    @SuppressWarnings("rawtypes")
    public interface Factory<T extends Slot> { Slot create(T value); }
    @SuppressWarnings("rawtypes")
    public interface CompareFactory<T extends Comparable> { T create(); }
    @SuppressWarnings("rawtypes")
    public static class Api {
        public Slot raw = new Slot<>(1);
        public Slot[] array;
        public Slot<Slot> nested;
        public Slot<? extends Slot> wildcard;
        public Slot rawResult() { return raw; }
        public Slot<String> exact() { return new Slot<>("value"); }
        public Slot callback(Factory<Slot> factory) { return factory.create(raw); }
        public String compareCallback(CompareFactory<String> factory) { return factory.create(); }
    }

    @Test
    void rendersRawOutputsAndBounds() throws Exception { writeFixture("plain"); }

    @Test
    void renamedExportsRetainRawArguments() throws Exception { writeFixture("renamed"); }

    @Test
    void editedSlotsKeepTheirExplicitArguments() throws Exception { writeFixture("edited"); }

    @Test
    void rawJavaValuesKeepTheirRuntimeErasure() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("api", new Api());
            assertEquals(1, context.eval("js", "api.rawResult().value").asInt());
            assertEquals("changed", context.eval("js", "api.raw.value = 'changed'; api.rawResult().value").asString());
            assertEquals("value", context.eval("js", "api.exact().value").asString());
        }
    }

    private static void writeFixture(String mode) throws Exception {
        var aliases = new TypeAliasRegistry();
        var renderer = new TypeScriptClassRenderer(aliases);
        var generator = new IndexFileGenerator(renderer, new AdapterAliasGenerator(aliases));
        List<Class<?>> types = List.of(Slot.class, RawBound.class, NestedBound.class, RawComparable.class,
                Factory.class, CompareFactory.class, Api.class, Comparable.class);
        Set<String> names = new LinkedHashSet<>();
        types.forEach(type -> names.add(type.getName()));
        List<TypeDecl> declarations = types.stream().map(type -> new TypeReflector().reflect(type)).toList();
        for (TypeDecl declaration : declarations) {
            if (mode.equals("renamed") && declaration.sourceClass == Slot.class) {
                declaration.renameTo = "RenamedSlot";
                declaration.mutated = true;
            }
            if (mode.equals("edited") && declaration.sourceClass == RawBound.class) {
                TypeSlot bound = declaration.typeParams.getFirst().bound;
                bound.ref = ApiTypeRef.symbol(new ApiSymbolId("java", Slot.class.getName()),
                        List.of(ApiTypeRef.primitive("string")));
                bound.overridden = true;
                declaration.mutated = true;
            }
            if (mode.equals("edited") && declaration.sourceClass == Api.class) {
                TypeSlot field = declaration.fields.stream().filter(value -> value.name.equals("raw"))
                        .findFirst().orElseThrow().type;
                field.ref = ApiTypeRef.symbol(new ApiSymbolId("java", Slot.class.getName()),
                        List.of(ApiTypeRef.primitive("string")));
                field.overridden = true;
                TypeSlot result = declaration.methods.stream().filter(value -> value.name.equals("rawResult"))
                        .findFirst().orElseThrow().returnType;
                result.ref = ApiTypeRef.primitive("boolean");
                result.overridden = true;
                declaration.mutated = true;
            }
        }
        generator.prepareFunctionalInterfaceAliases(names, Set.of());
        declarations.forEach(declaration -> generator.predeclareClass(declaration.fqn, declaration, Set.of()));
        String module = generator.generate(Api.class.getPackageName(), types.stream()
                .filter(type -> type.getPackageName().equals(Api.class.getPackageName()))
                .map(type -> type.getName().substring(type.getPackageName().length() + 1)).toList(), List.of(), names);
        Path output = Path.of("build/probe-ts-raw-output", mode);
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), module);
        Files.writeString(output.resolve("java.d.ts"),
                generator.generate("java.lang", List.of("Comparable"), List.of(), names));
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptRawOutputTest$Api as Api, $TypeScriptRawOutputTest$Slot as Slot,
                    $TypeScriptRawOutputTest$RawBound as RawBound, $TypeScriptRawOutputTest$NestedBound as NestedBound,
                    $TypeScriptRawOutputTest$RawComparable as RawComparable
                    } from 'java:com/tkisor/nekojs/probe/ir';
                declare const api: Api;
                const exact: string = api.exact().value;
                api.array[0].value = 'raw';
                api.nested.value.value = 7;
                api.wildcard.value.value = true;
                api.callback(value => value);
                api.compareCallback(() => 'value');
                declare const bounded: RawBound<Slot<string>>;
                const value: string = bounded.value.value;
                declare const nested: NestedBound<Slot<Slot<number>>>;
                const number: number = nested.value.value.value;
                const comparableNumber: number = RawComparable.numbers().value;
                declare const comparableString: RawComparable<string>;
                // @ts-expect-error Raw Comparable still requires a compatible Java host or primitive.
                declare const invalidComparable: RawComparable<{ unrelated: string }>;
                // @ts-expect-error An explicit type argument still preserves its value type.
                const wrong: number = api.exact().value;
                // @ts-expect-error A raw upper bound is still a host bound.
                declare const invalid: RawBound<number>;
                """ + (mode.equals("edited") ? """
                const edited: string = api.raw.value;
                const editedResult: boolean = api.rawResult();
                // @ts-expect-error An edited field must not revert to raw erasure.
                api.raw.value = 7;
                // @ts-expect-error An edited bound must not revert to raw erasure.
                declare const invalidEdited: RawBound<Slot<number>>;
                """ : ""));
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                "paths":{"java:com/tkisor/nekojs/probe/ir":["./index.d.ts"],"java:java/lang":["./java.d.ts"]}},
                "files":["index.d.ts","java.d.ts","exercise.ts"]}
                """);
        String prefix = "$TypeScriptRawOutputTest$";
        assertTrue(module.contains(prefix + "NestedBound<T extends " + prefix + "Slot<"
                + prefix + "Slot<any>>>"), module);
        assertEquals(0, TypeReflector.toRef(Slot.class).arguments().size());
        TypeSlot editedComparable = TypeSlot.of(Comparable.class, TypeReflector.toRef(Comparable.class));
        editedComparable.overridden = true;
        assertEquals("$Comparable", TypeScriptClassRenderer.renderTypeParameterBound(
                editedComparable, "T", aliases, new LinkedHashSet<>()));
    }
}
