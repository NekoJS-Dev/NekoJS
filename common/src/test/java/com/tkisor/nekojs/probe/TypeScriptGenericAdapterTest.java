package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptGenericAdapterTest {
    public static class Box<T> {
        public Box(T value) {}
        public T get() { return null; }
    }
    public static class Payload { public String name() { return "payload"; } }
    public static class Alternate extends Payload { public int alternate() { return 1; } }
    public static class Bounded<T extends Payload> {
        public Bounded(T value) {}
        public T get() { return null; }
    }
    public static class Recursive<T extends Recursive<T>> { public T get() { return null; } }
    public static class Node extends Recursive<Node> { public String label() { return "node"; } }
    public static class Calls {
        public void box(Box<String> value) {}
        public void bounded(Bounded<Alternate> value) {}
        public void recursive(Recursive<Node> value) {}
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void preservesArgumentsBoundsAndRawAliasCompatibility(@TempDir Path directory) throws Exception {
        writeFixture("plain", directory);
    }

    @Test
    void renamedTargetKeepsGenericCanonicalAlias(@TempDir Path directory) throws Exception {
        writeFixture("renamed", directory);
    }

    @Test
    void editedBoundsAreTheAliasSourceOfTruth(@TempDir Path directory) throws Exception {
        writeFixture("edited", directory);
    }

    @Test
    void moduleViewsRetainGenericAliasesAndClearRemovesTheirArity() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        registry.registerAdapterAlias(Box.class.getName(), "$Box_", 1);
        TypeAliasRegistry module = registry.withReferences((fqn, name) -> name + "Local");
        assertEquals("$Box_Local<string>", module.renderRegisteredAlias(Box.class.getName(), List.of("string")));
        assertEquals("$Box_", registry.getRegisteredAlias(Box.class.getName()));
        registry.clear();
        registry.registerClassAlias(Box.class.getName(), "string");
        assertEquals("string", registry.renderRegisteredAlias(Box.class.getName(), List.of("number")));
        assertEquals("$Box_Local<number>", module.renderRegisteredAlias(Box.class.getName(), List.of("number")));
    }

    private static void writeFixture(String mode, Path directory) throws Exception {
        List<Class<?>> classes = List.of(Box.class, Payload.class, Alternate.class, Bounded.class,
                Recursive.class, Node.class, Calls.class);
        List<TypeDecl> declarations = classes.stream().map(c -> new TypeReflector().reflect(c)).toList();
        for (TypeDecl declaration : declarations) {
            if (mode.equals("renamed") && declaration.sourceClass == Box.class) {
                declaration.renameTo = "RenamedBox";
                declaration.mutated = true;
            }
            if (mode.equals("edited") && declaration.sourceClass == Bounded.class) {
                declaration.typeParams.getFirst().bound = TypeSlot.of(null, TypeReflector.toRef(Alternate.class));
                declaration.mutated = true;
            }
            if (mode.equals("edited") && declaration.sourceClass == Calls.class) {
                declaration.methods.stream().filter(method -> method.name.equals("box")).findFirst()
                        .orElseThrow().params.getFirst().type.overridden = true;
                declaration.mutated = true;
            }
        }
        List<AdapterCatalogEntry> adapters = List.of(Box.class, Bounded.class, Recursive.class).stream()
                .map(c -> new AdapterCatalogEntry(c, List.of(AdapterInputShape.self(), AdapterInputShape.string(),
                        AdapterInputShape.arrayOf(AdapterInputShape.self()),
                        AdapterInputShape.object(AdapterInputShape.Slot.req("next", AdapterInputShape.self()))),
                        ConversionPrecedence.LOWEST, Optional.empty())).toList();
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), adapters,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 3, "SMART"));
        var context = new ProbeContext.Of(snapshot, classes, config, NekoJSPaths.fromGameDir(directory),
                "typescript", directory.resolve("types"), declarations);
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-generic-adapter", mode);
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        String prefix = "$TypeScriptGenericAdapterTest$";
        String usage = """
                import { $TypeScriptGenericAdapterTest$Box as Box,
                    $TypeScriptGenericAdapterTest$Box_ as BoxInput,
                    $TypeScriptGenericAdapterTest$Bounded as Bounded,
                    $TypeScriptGenericAdapterTest$Bounded_ as BoundedInput,
                    $TypeScriptGenericAdapterTest$Recursive_ as RecursiveInput,
                    $TypeScriptGenericAdapterTest$Payload as Payload,
                    $TypeScriptGenericAdapterTest$Alternate as Alternate,
                    $TypeScriptGenericAdapterTest$Node as Node,
                    $TypeScriptGenericAdapterTest$Calls as Calls } from 'java:com/tkisor/nekojs/probe';
                declare const calls: Calls;
                calls.box(new Box('value'));
                calls.box('encoded');
                calls.box([new Box('value')]);
                calls.box({ next: new Box('value') });
                const raw: BoxInput = new Box(42);
                const exact: BoxInput<string> = new Box('value');
                // @ts-expect-error Explicit arguments preserve the host value type.
                calls.box(new Box(42));
                // @ts-expect-error Nested Self forwards the same host argument.
                calls.box([new Box(42)]);
                // @ts-expect-error Object Self forwards the same host argument.
                calls.box({ next: new Box(42) });
                // @ts-expect-error Direct alias uses retain their explicit argument.
                const wrong: BoxInput<string> = new Box(42);
                calls.bounded(new Bounded(new Alternate()));
                // @ts-expect-error The target's upper bound is retained.
                declare const invalidBound: BoundedInput<string>;
                declare const recursive: RecursiveInput<Node>;
                calls.recursive(recursive);
                // @ts-expect-error Recursive bounds are retained.
                declare const invalidRecursive: RecursiveInput<Payload>;
                """;
        if (mode.equals("edited")) usage += "// @ts-expect-error The edited bound replaces the reflected one.\n";
        usage += "declare const baseBound: BoundedInput<Payload>;\n";
        Files.writeString(output.resolve("usage.ts"), usage);
        Files.writeString(output.resolve("tsconfig.json"), """
                { "compilerOptions": { "strict": true, "skipLibCheck": false, "noEmit": true,
                    "target": "ES2022", "module": "ESNext", "moduleResolution": "Bundler", "types": [],
                    "baseUrl": ".", "paths": { "java:*": ["./@package/*"] } },
                  "include": ["usage.ts", "@package/**/*.d.ts"] }
                """);
        String module = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertTrue(module.contains(prefix + "Box_<T"), module);
        assertTrue(module.contains("box(value: " + prefix + "Box_<string>)"), module);
        String bound = prefix + (mode.equals("edited") ? "Alternate" : "Payload");
        assertTrue(module.contains(prefix + "Bounded_<T extends " + bound), module);
    }
}
