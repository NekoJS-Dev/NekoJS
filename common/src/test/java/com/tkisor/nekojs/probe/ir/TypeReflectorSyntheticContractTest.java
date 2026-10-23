package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TypeReflectorSyntheticContractTest {
    public static class Box<T> {
        public final T value;
        public Box(T value) { this.value = value; }
    }

    public interface Values<T> {
        Box<T> getValues();
        @Remap("ownLabel")
        String alias();
        @HideFromJS
        String secret();
    }

    @Test
    void syntheticInterfaceImplementationsRemainCallableAndTyped() throws Exception {
        Class<?> host = syntheticHost();
        assertTrue(host.getMethod("getValues").isSynthetic());
        assertFalse(host.getMethod("getValues").isBridge());
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true).option("js.nashorn-compat", "true")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("host", host.getConstructor().newInstance());
            assertEquals("owned", context.eval("js", "host.getValues().value").asString());
            assertEquals("owned", context.eval("js", "host.values.value").asString());
        }
        var declaration = new TypeReflector().reflect(host);
        writeTypeScript(host, declaration);
        var getter = declaration.methods.stream().filter(method -> method.name.equals("getValues"))
                .findFirst().orElseThrow();
        assertEquals(List.of(TypeReflector.toRef(String.class)), getter.returnType.ref.arguments());
        assertEquals(host.getMethod("getValues"), getter.sourceMethod);
        assertTrue(getter.isGetter);
        assertFalse(declaration.methods.stream().anyMatch(method -> method.name.equals("noise")));
        assertFalse(declaration.methods.stream().anyMatch(method -> method.name.equals("secret")));
        assertEquals("ownLabel", declaration.methods.stream().filter(method -> method.name.equals("alias"))
                .findFirst().orElseThrow().effectiveName());
        assertEquals(1, declaration.methods.stream().filter(method -> method.name.equals("compareTo")).count());
    }

    private static void writeTypeScript(Class<?> host, TypeDecl declaration) throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> types = List.of(host, Values.class, Box.class, Comparable.class);
        Set<String> names = types.stream().map(Class::getName).collect(Collectors.toSet());
        generator.prepareFunctionalInterfaceAliases(names, Set.of());
        for (Class<?> type : types) {
            generator.predeclareClass(type.getName(), type == host ? declaration : new TypeReflector().reflect(type), Set.of());
        }
        Path output = Path.of("build/probe-ts-synthetic-contract");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), generator.generate(SyntheticContractHost.class.getPackageName(),
                List.of("SyntheticContractHost", "TypeReflectorSyntheticContractTest$Values",
                        "TypeReflectorSyntheticContractTest$Box"), List.of(), names));
        Files.writeString(output.resolve("java.d.ts"), generator.generate("java.lang", List.of("Comparable"), List.of(), names));
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $SyntheticContractHost as Host } from 'java:com/tkisor/nekojs/probe/ir';
                declare const host: Host;
                const result: string = host.getValues().value;
                const bean: string = host.values.value;
                const label: string = host.ownLabel();
                // @ts-expect-error The recovered contract retains its element type.
                const wrong: number = host.getValues().value;
                // @ts-expect-error Unrelated synthetic methods remain implementation details.
                host.noise();
                // @ts-expect-error An explicit remap retains ownership of the method name.
                host.alias();
                // @ts-expect-error Hidden implementation methods remain hidden.
                host.secret();
                """);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                "paths":{"java:com/tkisor/nekojs/probe/ir":["./index.d.ts"],"java:java/lang":["./java.d.ts"]}},
                "files":["index.d.ts","java.d.ts","exercise.ts"]}
                """);
    }

    /** Models an injected accessor by changing only its method flags, preserving code and annotations. */
    private static Class<?> syntheticHost() throws Exception {
        byte[] bytes;
        try (var input = SyntheticContractHost.class.getResourceAsStream("SyntheticContractHost.class")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        assertEquals(0xCAFEBABE, buffer.getInt());
        buffer.position(8);
        String[] names = new String[Short.toUnsignedInt(buffer.getShort())];
        for (int index = 1; index < names.length; index++) {
            int tag = Byte.toUnsignedInt(buffer.get());
            switch (tag) {
                case 1 -> {
                    int size = Short.toUnsignedInt(buffer.getShort());
                    byte[] encoded = new byte[size + 2];
                    ByteBuffer.wrap(encoded).putShort((short) size);
                    buffer.get(encoded, 2, size);
                    names[index] = new DataInputStream(new java.io.ByteArrayInputStream(encoded)).readUTF();
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> buffer.position(buffer.position() + 4);
                case 5, 6 -> { buffer.position(buffer.position() + 8); index++; }
                case 7, 8, 16, 19, 20 -> buffer.position(buffer.position() + 2);
                case 15 -> buffer.position(buffer.position() + 3);
                default -> throw new AssertionError("Unexpected fixture constant tag: " + tag);
            }
        }
        buffer.position(buffer.position() + 6);
        int interfaces = Short.toUnsignedInt(buffer.getShort());
        buffer.position(buffer.position() + interfaces * 2);
        for (int section = 0; section < 2; section++) {
            int members = Short.toUnsignedInt(buffer.getShort());
            for (int index = 0; index < members; index++) {
                int flagsAt = buffer.position();
                short flags = buffer.getShort();
                String name = names[Short.toUnsignedInt(buffer.getShort())];
                buffer.getShort();
                if (section == 1 && Set.of("getValues", "alias", "secret", "noise").contains(name)) {
                    buffer.putShort(flagsAt, (short) (flags | 0x1000));
                }
                int attributes = Short.toUnsignedInt(buffer.getShort());
                for (int attribute = 0; attribute < attributes; attribute++) {
                    buffer.getShort();
                    int size = buffer.getInt();
                    buffer.position(buffer.position() + size);
                }
            }
        }
        return new ClassLoader(SyntheticContractHost.class.getClassLoader()) {
            Class<?> define() { return defineClass(SyntheticContractHost.class.getName(), bytes, 0, bytes.length); }
        }.define();
    }
}
