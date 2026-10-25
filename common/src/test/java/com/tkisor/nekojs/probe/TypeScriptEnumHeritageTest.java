package com.tkisor.nekojs.probe;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptEnumHeritageTest {
    enum Color { RED, BLUE }
    public interface Labelled<T> {
        default T echo(T value) { return value; }
    }
    public interface SelfLabel<T> {
        default T identity(T value) { return value; }
    }
    enum LabelledColor implements Labelled<String>, SelfLabel<LabelledColor> { RED }

    @Test
    void hiddenEnumInterfaceRemainsUnavailableAndIsReported(@TempDir Path temp) {
        TestPlatformInit.ensureInitialized();
        TypeDecl hidden = new TypeReflector().reflect(Labelled.class);
        hidden.hidden = true;
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(LabelledColor.class), config,
                NekoJSPaths.fromGameDir(temp), "typescript", temp.resolve("typescript"), List.of(hidden));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        var report = JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("hidden", report.getAsJsonObject("unavailable").get(Labelled.class.getName()).getAsString());
        String declarations = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(declarations.contains("export interface $TypeScriptEnumHeritageTest$Labelled<"));
        // Keep the source contract visibly incomplete instead of fabricating an unrelated enum type.
        assertTrue(declarations.contains("$TypeScriptEnumHeritageTest$Labelled<string>"));
    }

    @Test
    void enumRetainsItsParameterizedInterfaceDefaultMethods() throws Exception {
        writeInterfaceFixture("interfaces", false, false);
    }

    @Test
    void renamedEnumRetainsItsInterfaceAndStaticFactory() throws Exception {
        writeInterfaceFixture("interfaces-renamed", true, false);
    }

    @Test
    void removedInterfaceIsNotRestoredFromEnumReflection() throws Exception {
        writeInterfaceFixture("interfaces-edited", false, true);
    }

    private static void writeInterfaceFixture(String fixture, boolean renamed, boolean removed) throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> types = List.of(Labelled.class, SelfLabel.class, LabelledColor.class);
        for (Class<?> type : types) {
            TypeDecl declaration = new TypeReflector().reflect(type);
            if (type == LabelledColor.class) {
                if (renamed) declaration.renameTo = "RenamedLabelledColor";
                if (removed) declaration.interfaces.clear();
                declaration.mutated = renamed || removed;
            }
            generator.predeclareClass(declaration.fqn, declaration, Set.of());
        }
        Set<String> included = Set.of(Labelled.class.getName(), SelfLabel.class.getName(),
                LabelledColor.class.getName(), Enum.class.getName());
        String output = generator.generate(Labelled.class.getPackageName(), types.stream()
                .map(type -> type.getName().substring(type.getPackageName().length() + 1)).toList(), List.of(), included);
        Path directory = Path.of("build/probe-ts-enum-heritage", fixture);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("enum.d.ts"), output);
        Files.writeString(directory.resolve("enum-base.d.ts"), """
                declare module "java:java/lang" {
                    export class $Enum<E extends $Enum<E>> { compareTo(other: E): number; }
                }
                """);
        Files.writeString(directory.resolve("usage.ts"), removed ? """
                import { $TypeScriptEnumHeritageTest$LabelledColor as Color,
                    $TypeScriptEnumHeritageTest$Labelled as Labelled } from "java:com/tkisor/nekojs/probe";
                // @ts-expect-error Removed interface defaults are not implicitly restored.
                Color.RED.echo("value");
                // @ts-expect-error Removing the interface also removes structural assignability.
                const instance: Labelled<string> = Color.RED;
                """ : """
                import { $TypeScriptEnumHeritageTest$LabelledColor as Color,
                    $TypeScriptEnumHeritageTest$Labelled as Labelled } from "java:com/tkisor/nekojs/probe";
                const instance: Labelled<string> = Color.RED;
                const result: string = Color.RED.echo("value");
                const parsed: Color = Color.valueOf("RED");
                const self: Color = Color.RED.identity(Color.RED);
                // @ts-expect-error A string cannot replace the enum's contextual self type.
                Color.RED.identity("RED");
                // @ts-expect-error The inherited interface keeps its contextual element type.
                Color.RED.echo(123);
                // @ts-expect-error The default method does not produce a number.
                const wrong: number = instance.echo("value");
                """);
        Files.writeString(directory.resolve("tsconfig.json"), """
                { "compilerOptions": { "strict": true, "skipLibCheck": false, "noEmit": true, "types": [],
                    "baseUrl": ".", "paths": { "java:com/tkisor/nekojs/probe": ["./enum.d.ts"] } },
                  "include": ["*.ts"] }
                """);
        if (removed) assertFalse(output.contains("$TypeScriptEnumHeritageTest$Labelled<string>"), output);
        else assertTrue(output.contains("$TypeScriptEnumHeritageTest$Labelled<string>"), output);
    }

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
