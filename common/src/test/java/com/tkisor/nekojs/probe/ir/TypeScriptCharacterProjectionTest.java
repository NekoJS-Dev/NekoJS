package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptCharacterProjectionTest {
    public interface Identity<T> { T copy(T value); }
    public interface Characters extends Identity<Character> {
        @Override Character copy(Character value);
        Character[] letters();
    }
    public interface Mapper<T, R> { R apply(T value); }
    public static class Holder<T> { public T value; }
    public static class Api {
        public Holder<Character> holder;
        public Character letter;
        public Character[] letters;
        public Api(Character value) {
            letter = value;
            holder = new Holder<>();
            holder.value = value;
            letters = new Character[]{value};
        }
        public Character copy(Character value) { return value; }
        public char primitive() { return 'a'; }
        public Character boxed() { return 'a'; }
        public Character held() { return holder.value; }
        public Identity<Character> identity() { return value -> value; }
        public Character apply(Mapper<Character, Character> callback) { return callback.apply('a'); }
    }

    @Test
    void boxedCharactersUseTheSameValueTypeInGenericsReturnsAndInputs() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> types = List.of(Identity.class, Characters.class, Mapper.class, Holder.class, Api.class);
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type : types) names.add(type.getName());
        Set<String> eligible = new LinkedHashSet<>(names);
        eligible.add(Character.class.getName());
        generator.prepareFunctionalInterfaceAliases(eligible, Set.of());
        for (Class<?> type : types) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        String declaration = generator.generate(Api.class.getPackageName(), types.stream()
                .map(type -> type.getName().substring(type.getPackageName().length() + 1)).toList(), List.of(), names);
        Path output = Path.of("build/probe-ts-character-projection");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declaration);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptCharacterProjectionTest$Api as Api } from 'java:com/tkisor/nekojs/probe/ir';
                import type { $TypeScriptCharacterProjectionTest$Characters as Characters, $TypeScriptCharacterProjectionTest$Identity as Identity } from 'java:com/tkisor/nekojs/probe/ir';
                declare const characters: Characters;
                const inherited: Identity<string> = characters;
                const copied: string = inherited.copy('a');
                const letters: string[] = characters.letters();
                const api = new Api('a');
                const boxed: string = api.boxed();
                const primitive: string = api.primitive();
                const nested: string = api.holder.value;
                const result: string = api.apply(value => value.toUpperCase());
                api.letter = 'b';
                api.letters = ['a', 'b'];
                // @ts-expect-error Character input is not an arbitrary host object.
                api.copy({});
                // @ts-expect-error Character results retain the string value contract.
                const wrongReturn: number = api.boxed();
                // @ts-expect-error Nested host type arguments use the same value contract.
                api.holder.value = 1;
                // @ts-expect-error Callback results retain their actual character contract.
                api.apply(value => 1);
                """);
        assertTrue(declaration.contains("extends $TypeScriptCharacterProjectionTest$Identity<string>"), declaration);
        assertFalse(java.util.regex.Pattern.compile("(?<![\\w$])\\$Character(?![\\w$])")
                .matcher(declaration).find(), declaration);
    }

    @Test
    void characterProjectionKeepsSharedIrAndExplicitSlotEdits() {
        ApiTypeRef original = TypeReflector.toRef(Character.class);
        assertEquals(ApiTypeRef.Kind.SYMBOL, original.kind());
        TypeDecl declaration = new TypeReflector().reflect(Api.class);
        TypeSlot letter = declaration.fields.stream().filter(field -> field.name.equals("letter"))
                .findFirst().orElseThrow().type;
        letter.ref = ApiTypeRef.primitive("boolean");
        letter.overridden = true;
        String text = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(text.contains("letter: boolean;"), text);
        assertEquals(original, TypeReflector.toRef(Character.class));
    }

    @Test
    void productionHostAccessUsesCharacterStringsForHostReturnsAndGenericCallbacks() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .build()) {
            context.getBindings("js").putMember("api", new Api('a'));
            assertEquals("string", context.eval("js", "typeof api.boxed()").asString());
            assertEquals("a", context.eval("js", "api.boxed()").asString());
            assertEquals("a", context.eval("js", "api.primitive()").asString());
            assertEquals("string", context.eval("js", "typeof api.holder.value").asString());
            assertEquals("a", context.eval("js", "api.holder.value").asString());
            assertEquals("a", context.eval("js", "api.letters[0]").asString());
            // Erased Object slots do not promise Character boxing from arbitrary guest strings.
            PolyglotException erasedWrite = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "api.holder.value = 'b'; api.held()"));
            assertTrue(erasedWrite.getMessage().contains("java.lang.String cannot be cast to class java.lang.Character"));
            PolyglotException erasedCall = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "api.identity().copy('c')"));
            assertTrue(erasedCall.getMessage().contains("java.lang.String cannot be cast to class java.lang.Character"));
            assertEquals("b", context.eval("js", "api.copy('b')").asString());
            assertEquals("A", context.eval("js", "api.apply(value => value.toUpperCase())").asString());
            assertThrows(PolyglotException.class, () -> context.eval("js", "api.copy('too long')"));
            assertThrows(PolyglotException.class, () -> context.eval("js", "api.copy({})"));
        }
    }
}
