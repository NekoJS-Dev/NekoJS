package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("rawtypes")
class TypeScriptRawInputTest {
    public interface Listener { void listen(); }
    public static class Payload implements Listener { public void listen() {} }
    public static class Raw<T extends Listener> { public T value; }
    public static class Pair<L, R> { public L first; public R second; }
    public static class Api {
        public void raw(Raw value) {}
        public void array(Raw[][] values) {}
        public void typed(Raw<Payload> value) {}
        public void edited(Raw value) {}
        public void nested(Pair<Raw, Payload> value) {}
        public void wildcard(Pair<? extends Raw[], Payload> value) {}
    }

    @Test
    void rawReflectionInputsEraseAtTheUseSiteAndKeepModuleReferences() throws Exception {
        TypeAliasRegistry registry = new TypeAliasRegistry().withReferences((fqn, name) -> "Local" + name);
        assertEquals("Local$Class<any>[][]", input(Class[][].class, registry));
        assertEquals("Local$TypeScriptRawInputTest$Pair<any, any>", input(Pair.class, registry));
        assertTrue(TypeReflector.toRef(Pair.class).arguments().isEmpty());
        registry.registerClassAlias(Raw.class.getName(), "$ManualRaw");
        assertEquals("Local$ManualRaw", input(Raw.class, registry));
        var nested = Api.class.getMethod("nested", Pair.class).getGenericParameterTypes()[0];
        assertEquals("Local$TypeScriptRawInputTest$Pair<Local$TypeScriptRawInputTest$Raw<any>, Local$TypeScriptRawInputTest$Payload>",
                input(nested, registry));
    }

    @Test
    void realDeclarationsPreserveExplicitAndEditedArgumentsWithoutClassDefaults() throws Exception {
        var reflector = new TypeReflector();
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        TypeDecl api = reflector.reflect(Api.class);
        TypeSlot typed = api.methods.stream().filter(method -> method.name.equals("typed")).findFirst().orElseThrow().params.getFirst().type;
        TypeSlot edited = api.methods.stream().filter(method -> method.name.equals("edited")).findFirst().orElseThrow().params.getFirst().type;
        edited.ref = typed.ref;
        edited.overridden = true;
        StringBuilder declarations = new StringBuilder("declare module 'raw-input-fixture' {\n");
        for (Class<?> type : List.of(Listener.class, Payload.class, Raw.class, Pair.class)) declarations.append(renderer.render(reflector.reflect(type)));
        declarations.append(renderer.render(api)).append("}\n");
        Path output = Path.of("build/probe-ts-raw-input");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declarations);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptRawInputTest$Api as Api, $TypeScriptRawInputTest$Raw as Raw, $TypeScriptRawInputTest$Payload as Payload } from 'raw-input-fixture';
                declare const api: Api;
                api.raw({value: 1});
                api.array([[{value: 1}]]);
                const valid: Raw<Payload> = {value: {listen() {}}};
                api.typed(valid);
                api.edited(valid);
                api.nested({first: {value: 1}, second: {listen() {}}});
                api.wildcard({first: [{value: 1}], second: {listen() {}}});
                // @ts-expect-error Explicit host arguments retain their member types.
                api.typed({value: 1});
                // @ts-expect-error An edited slot must not regain reflection's raw erasure.
                api.edited({value: 1});
                // @ts-expect-error Raw use sites do not remove the declared bound.
                type Invalid = Raw<number>;
                // @ts-expect-error Erasing one raw argument must preserve its typed sibling.
                api.nested({first: {value: 1}, second: 1});
                """);
        String text = declarations.toString();
        assertTrue(text.contains("raw(value: $TypeScriptRawInputTest$Raw<any>)"), text);
        assertTrue(text.contains("typed(value: $TypeScriptRawInputTest$Raw<$TypeScriptRawInputTest$Payload>)"), text);
        assertTrue(text.contains("edited(value: $TypeScriptRawInputTest$Raw<$TypeScriptRawInputTest$Payload>)"), text);
        assertTrue(text.contains("class $TypeScriptRawInputTest$Raw<T extends $TypeScriptRawInputTest$Listener>"), text);
    }

    private static String input(java.lang.reflect.Type type, TypeAliasRegistry registry) {
        return FunctionalInterfaceAliasGenerator.renderJavaTypeInput(type, registry, new java.util.LinkedHashSet<>());
    }
}
