package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptEnumHeritageTest {
    enum Color { RED, BLUE }

    @Test
    void enumInstancesMeetJavaEnumBoundsWithoutInheritingItsStaticFactory() throws Exception {
        writeFixture("plain", null);
    }

    @Test
    void renamedEnumUsesItsEffectiveSelfTypeAndKeepsCanonicalExports() throws Exception {
        writeFixture("renamed", "RenamedColor");
    }

    @Test
    void enumNamedLikeItsJavaBaseKeepsTheImportedBaseDistinct() throws Exception {
        writeFixture("collision", "Enum");
    }

    private static void writeFixture(String fixture, String renamed) throws Exception {
        TypeDecl declaration = new TypeReflector().reflect(Color.class);
        declaration.renameTo = renamed;
        declaration.mutated = renamed != null;
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        generator.predeclareClass(declaration.fqn, declaration, Set.of());
        String simpleName = Color.class.getName().substring(Color.class.getPackageName().length() + 1);
        String output = generator.generate(Color.class.getPackageName(), List.of(simpleName), List.of(),
                Set.of(Color.class.getName(), Enum.class.getName()));
        Path directory = Path.of("build/probe-ts-enum-heritage", fixture);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("enum.d.ts"), output);
        Files.writeString(directory.resolve("enum-base.d.ts"), """
                declare module "java:java/lang" {
                    export class $Enum<E extends $Enum<E>> {
                        compareTo(other: E): number;
                        static valueOf<T extends $Enum<T>>(kind: object, name: string): T;
                    }
                }
                """);
        Files.writeString(directory.resolve("usage.ts"), """
                import { $TypeScriptEnumHeritageTest$Color as Color } from "java:com/tkisor/nekojs/probe";
                import { $Enum } from "java:java/lang";
                function acceptEnum<E extends $Enum<E>>(value: E): E { return value; }
                const exact: Color = acceptEnum(Color.RED);
                const parsed: Color = Color.valueOf("BLUE");
                const comparison: number = exact.compareTo(parsed);
                // @ts-expect-error The enum factory retains its one-argument signature.
                Color.valueOf({}, "RED");
                // @ts-expect-error A string is not a Java enum instance.
                acceptEnum("RED");
                """);
        Files.writeString(directory.resolve("tsconfig.json"), """
                { "compilerOptions": { "strict": true, "skipLibCheck": false, "noEmit": true, "types": [],
                    "baseUrl": ".", "paths": { "java:com/tkisor/nekojs/probe": ["./enum.d.ts"] } },
                  "include": ["*.ts"] }
                """);
        String effective = renamed == null ? simpleName : renamed;
        assertTrue(output.contains("export interface $" + effective + " extends "), output);
        assertTrue(output.contains("export class $" + effective + " {"), output);
        if (renamed != null) {
            assertTrue(output.contains("<$" + effective + ">"), output);
        }
    }
}
