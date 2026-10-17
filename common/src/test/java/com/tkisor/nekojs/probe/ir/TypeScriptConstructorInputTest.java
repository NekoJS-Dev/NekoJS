package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptConstructorInputTest {
    public interface Listener { void listen(); }
    public static class Payload implements Listener {
        public String label;
        public void listen() {}
    }
    public static class Box<T> { public T value; }
    public static class Api<L extends Listener> {
        public <S extends Payload> Api(Box<S> value, L owner) {}
        public <S> Api(S[] values) {}
        public <S extends L> Api(int marker, S owner) {}
        public <S extends Payload> S identity(S value) { return value; }
        public L owner(L value) { return value; }
    }

    @Test
    void constructorLocalVariablesKeepInputBoundsAndOtherScopes() throws Exception {
        var reflector = new TypeReflector();
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        StringBuilder text = new StringBuilder("declare module 'constructor-input-fixture' {\n");
        for (Class<?> type : List.of(Listener.class, Payload.class, Box.class, Api.class)) {
            text.append(renderer.render(reflector.reflect(type)));
        }
        text.append("}\n");
        Path output = Path.of("build/probe-ts-constructor-input");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), text);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptConstructorInputTest$Api as Api, $TypeScriptConstructorInputTest$Payload as Payload } from 'constructor-input-fixture';
                declare const payload: Payload;
                const api = new Api<Payload>({value: payload}, payload);
                new Api<Payload>([1, 'text']);
                new Api<Payload>(1, payload);
                const result: Payload = api.identity(payload);
                // @ts-expect-error Constructor-local bounds remain meaningful inside host containers.
                new Api<Payload>({value: 1}, payload);
                // @ts-expect-error Class-level variables are not erased along with constructor locals.
                new Api<Payload>({value: payload}, 1);
                // @ts-expect-error A constructor-local bound on a class variable retains that class scope.
                new Api<Payload>(1, {listen() {}});
                // @ts-expect-error Generic methods retain their declared bounds.
                api.identity({listen() {}});
                // @ts-expect-error Class-level method inputs remain precise.
                api.owner(1);
                """);
        assertTrue(text.toString().contains("identity<S extends $TypeScriptConstructorInputTest$Payload>"), text.toString());
        assertTrue(text.toString().contains("constructor(value: $TypeScriptConstructorInputTest$Box<$TypeScriptConstructorInputTest$Payload>, owner: L)"), text.toString());
    }

    @Test
    void projectedReflectionTypesHaveStableStructuralIdentity() throws Exception {
        var source = Api.class.getConstructor(Box.class, Listener.class).getGenericParameterTypes()[0];
        var first = ConstructorInputTypes.project(source);
        var second = ConstructorInputTypes.project(source);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(Box.class.getName() + "<" + Payload.class.getName() + ">", first.getTypeName());
    }

    @Test
    void explicitConstructorSlotEditsRemainAuthoritative() {
        var reflector = new TypeReflector();
        TypeDecl declaration = reflector.reflect(Api.class);
        TypeSlot slot = declaration.constructors.stream().filter(ctor -> ctor.params.size() == 1)
                .findFirst().orElseThrow().params.getFirst().type;
        slot.ref = ApiTypeRef.primitive("string");
        slot.overridden = true;
        String text = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(text.contains("constructor(values: string)"), text);
        assertEquals(slot.ref, ApiTypeRef.primitive("string"));
    }
}
