package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptObjectProjectionTest {
    public interface Parent { Object value(); }
    public interface Child<T> extends Parent { @Override T value(); }
    public interface Mapper { Object map(Object value); }
    public interface GenericMapper<T> { T map(T value); }
    public interface BoundedMapper<T extends Holder<Object>> { T map(T value); }
    public static class Holder<T> { public T value; }
    public static class Bounded<T extends Holder<Object>> { public T value; }
    public static class Api {
        public Object field;
        public Holder<Object> holder;
        public Object[] array;
        public Api(Object value) { field = value; }
        public Object echo(Object value) { return value; }
        public Object[] objects(Object[] values) { return values; }
        public Holder<Object> nested(Holder<Object> value) { return value; }
        public Object callback(Mapper mapper) { return mapper.map(17); }
        public Object genericCallback(GenericMapper<Object> mapper) { return mapper.map(true); }
        public <T extends Holder<Object>> T bounded(T value) { return value; }
        public void boundedCallback(BoundedMapper<Holder<Object>> mapper) {}
    }

    @Test
    void objectValuesIncludePrimitivesInDirectNestedAndCallbackSignatures() throws Exception {
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), adapters);
        List<Class<?>> types = List.of(Parent.class, Child.class, Mapper.class, GenericMapper.class,
                BoundedMapper.class, Holder.class, Bounded.class, Api.class);
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type : types) names.add(type.getName());
        List<TypeDecl> declarations = types.stream().map(type -> new TypeReflector().reflect(type)).toList();
        declarations.stream().filter(declaration -> declaration.sourceClass == Bounded.class).forEach(declaration -> {
            declaration.renameTo = "ObjectBounded";
            declaration.mutated = true;
        });
        adapters.prepare(List.of(new AdapterCatalogEntry(Bounded.class, List.of(AdapterInputShape.self()),
                ConversionPrecedence.LOWEST, Optional.empty())), names, declarations);
        generator.prepareFunctionalInterfaceAliases(names, Set.of());
        for (TypeDecl declaration : declarations) generator.predeclareClass(declaration.fqn, declaration, Set.of());
        String declaration = generator.generate(Api.class.getPackageName(), types.stream()
                .map(type -> type.getName().substring(type.getPackageName().length() + 1)).toList(), List.of(), names);
        Path output = Path.of("build/probe-ts-object-projection");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declaration);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptObjectProjectionTest$Api as Api } from 'java:com/tkisor/nekojs/probe/ir';
                import type { $TypeScriptObjectProjectionTest$Child as Child, $TypeScriptObjectProjectionTest$Parent as Parent } from 'java:com/tkisor/nekojs/probe/ir';
                import type { $TypeScriptObjectProjectionTest$Bounded as Bounded, $TypeScriptObjectProjectionTest$Bounded_ as BoundedInput, $TypeScriptObjectProjectionTest$Holder as Holder } from 'java:com/tkisor/nekojs/probe/ir';
                declare const bounded: Bounded<Holder<unknown>>;
                const boundedInput: BoundedInput<Holder<unknown>> = bounded;
                declare const child: Child<string>;
                const parent: Parent = child;
                const api = new Api(null);
                api.echo(1); api.echo('value'); api.echo(false); api.echo(null); api.echo({key: 1});
                api.field = 'value';
                api.holder.value = 1;
                api.array = [1, null, false];
                const array: unknown[] = api.objects(['value', 1, null]);
                const nested: unknown = api.nested(api.holder).value;
                api.bounded(api.holder);
                api.boundedCallback(value => { value.value = 1; return value; });
                api.callback(value => {
                  if (typeof value === 'number') return value + 1;
                  return null;
                });
                api.genericCallback(value => {
                  if (typeof value === 'boolean') return !value;
                  return 'fallback';
                });
                // @ts-expect-error Object values require narrowing before primitive assignment.
                const number: number = api.echo(1);
                // @ts-expect-error Object values do not promise arbitrary structured members.
                api.echo(1).missing;
                // @ts-expect-error Callback arguments require narrowing too.
                api.callback(value => value.missing);
                """);
        assertTrue(declaration.contains("value(): unknown;"), declaration);
        assertTrue(declaration.contains("$TypeScriptObjectProjectionTest$Holder<unknown>"), declaration);
        assertTrue(declaration.contains("objects(values: unknown[]): unknown[];"), declaration);
    }

    @Test
    void projectionPreservesSharedIrExplicitObjectEditsAndRegisteredInputAdapters() {
        ApiTypeRef original = TypeReflector.toRef(Object.class);
        assertEquals(ApiTypeRef.primitive("object"), original);
        var declaration = new TypeReflector().reflect(Api.class);
        var field = declaration.fields.stream().filter(value -> value.name.equals("field")).findFirst().orElseThrow();
        field.type.overridden = true;
        field.type.ref = ApiTypeRef.primitive("object");
        var echo = declaration.methods.stream().filter(value -> value.name.equals("echo")).findFirst().orElseThrow();
        echo.params.getFirst().type.overridden = true;
        echo.params.getFirst().type.ref = ApiTypeRef.primitive("object");
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(Object.class.getName(), "boolean");
        String text = new TypeScriptClassRenderer(aliases).render(declaration);
        assertTrue(text.contains("field: object;"), text);
        assertTrue(text.contains("echo(value: object): unknown;"), text);
        String reflected = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(Api.class));
        assertTrue(reflected.contains("echo(value: boolean): unknown;"), reflected);
        assertEquals("object", TypeScriptClassRenderer.renderTypeRef(ApiTypeRef.primitive("object")));
        assertEquals(original, TypeReflector.toRef(Object.class));
    }

    @Test
    void productionHostAccessAcceptsBoxedPrimitiveAndNullObjectValues() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false)
                .allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("api", new Api(null));
            assertTrue(context.eval("js", "api.echo(null) === null").asBoolean());
            assertEquals(12, context.eval("js", "api.echo(12)").asInt());
            assertEquals("value", context.eval("js", "api.echo('value')").asString());
            assertFalse(context.eval("js", "api.echo(false)").asBoolean());
            assertEquals(18, context.eval("js", "api.callback(value => value + 1)").asInt());
            assertFalse(context.eval("js", "api.genericCallback(value => !value)").asBoolean());
            assertEquals("number", context.eval("js", "api.field = 3; typeof api.field").asString());
        }
    }
}
