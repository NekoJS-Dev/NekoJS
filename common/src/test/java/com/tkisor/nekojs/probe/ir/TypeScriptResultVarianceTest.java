package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptResultVarianceTest {
    public interface Producer<K, V> {
        V get(Object key);
        default void defaultReturnValue(V value) {}
    }

    @Test
    void resultAnnotationsPreserveHostAndCallbackInputRelations() throws Exception {
        var aliases = new TypeAliasRegistry();
        var renderer = new TypeScriptClassRenderer(aliases);
        renderer.setCovariantTypeParameters(Map.of(Producer.class.getName(), Set.of("V")));
        var generator = new IndexFileGenerator(renderer, new AdapterAliasGenerator(aliases));
        Set<String> types = Set.of(Producer.class.getName());
        generator.prepareFunctionalInterfaceAliases(types, Set.of());
        generator.predeclareClass(Producer.class.getName(), new TypeReflector().reflect(Producer.class), Set.of());
        String declaration = generator.generate(Producer.class.getPackageName(),
                List.of("TypeScriptResultVarianceTest$Producer"), List.of(), types);
        assertTrue(declaration.contains("$TypeScriptResultVarianceTest$Producer<K, out V>"), declaration);
        Path output = Path.of("build/probe-ts-result-variance");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declaration);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptResultVarianceTest$Producer as Producer, $TypeScriptResultVarianceTest$Producer_ as Input } from 'java:com/tkisor/nekojs/probe/ir';
                interface Animal { animal: true; }
                interface Dog extends Animal { dog: true; }
                declare const narrowResult: Producer<Animal, Dog>;
                declare const broadResult: Producer<Animal, Animal>;
                const covariant: Producer<Animal, Animal> = narrowResult;
                const hostInput: Input<Dog, Animal, object, Animal> = narrowResult;
                const callback: Input<Dog, Animal, object, Animal> = key => ({animal: true});
                // @ts-expect-error The original result relation still rejects a broader host result.
                const wrongResult: Producer<Animal, Dog> = broadResult;
                // @ts-expect-error Callback results retain their original bound.
                const wrongCallback: Input<Dog, Animal, object, Animal> = key => 1;
                """);
    }

    @Test
    void editedDeclarationsDoNotInheritAnOriginalLibraryVarianceRule() {
        TypeDecl declaration = new TypeReflector().reflect(Producer.class);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.setCovariantTypeParameters(Map.of(Producer.class.getName(), Set.of("V")));
        declaration.mutated = true;
        assertTrue(renderer.render(declaration).contains("$TypeScriptResultVarianceTest$Producer<K, V>"));
        assertFalse(renderer.render(declaration).contains("out V"));
    }
}
