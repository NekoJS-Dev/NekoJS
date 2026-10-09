package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.testfixture.ClassDeclarationFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDeclarationTypeScriptBackendTest {

    private static final String TARGET_NAME = "$ClassDeclarationTypeScriptBackendTest$Builder";
    private static final String DECLARATION = """
            export class $ClassDeclarationTypeScriptBackendTest$Builder<T = {}> {
                schema<const S extends Record<string, string>>(schema: S): $ClassDeclarationTypeScriptBackendTest$Builder<S>;
                use(value: $ClassDeclarationFixtures$Imported): $ClassDeclarationFixtures$Adapted_;
            }
            """;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void emptyDeclarationCatalogPreservesTheLegacySnapshotOutput(@TempDir Path tempDir) {
        NekoScriptCatalogSnapshot legacyShape = legacyEmptySnapshot();
        NekoScriptCatalogSnapshot explicitEmpty = snapshot(List.of(), List.of());

        assertEquals(render(legacyShape, List.of(Builder.class), null, tempDir),
                render(explicitEmpty, List.of(Builder.class), null, tempDir));
    }

    @Test
    void replacementEmitsOnceAndItsExplicitImportsAreReachable(@TempDir Path tempDir) {
        var entry = ClassDeclarationCatalogEntry.of(Builder.class, DECLARATION,
                Set.of(ClassDeclarationFixtures.Imported.class, ClassDeclarationFixtures.Adapted.class));
        var adapter = new AdapterCatalogEntry(ClassDeclarationFixtures.Adapted.class,
                List.of(AdapterInputShape.string()), ConversionPrecedence.LOWEST, Optional.empty());

        Map<String, String> files = render(snapshot(List.of(entry), List.of(adapter)),
                List.of(Builder.class), null, tempDir);

        String targetModule = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertNotNull(targetModule);
        assertEquals(1, occurrences(targetModule, "export class " + TARGET_NAME + "<"), targetModule);
        assertTrue(targetModule.contains("schema<const S extends Record<string, string>>"), targetModule);
        assertFalse(targetModule.contains("reflectedOnlyMethod"), targetModule);
        assertTrue(targetModule.contains("import { $ClassDeclarationFixtures$Adapted, "
                + "$ClassDeclarationFixtures$Adapted_, $ClassDeclarationFixtures$Imported } from "
                + "\"java:com/tkisor/nekojs/probe/testfixture\";"), targetModule);

        String dependencyModule = files.get("@package/com/tkisor/nekojs/probe/testfixture/index.d.ts");
        assertNotNull(dependencyModule, "an explicit import must cause its Java module to be generated");
        assertTrue(dependencyModule.contains("export class $ClassDeclarationFixtures$Imported"), dependencyModule);
        assertTrue(dependencyModule.contains("export type $ClassDeclarationFixtures$Adapted_"), dependencyModule);
    }

    @Test
    void enumReplacementRetainsOneCanonicalInputAliasDespiteIrRenaming(@TempDir Path tempDir) {
        String name = "$ClassDeclarationTypeScriptBackendTest$Color";
        var entry = ClassDeclarationCatalogEntry.of(Color.class,
                "export class " + name + " { static RED: " + name + "; code(): number; }");
        TypeDecl edited = new com.tkisor.nekojs.probe.ir.TypeReflector().reflect(Color.class);
        edited.renameTo = "EditedColor";
        edited.mutated = true;
        Map<String, String> files = render(snapshot(List.of(entry), List.of()),
                List.of(Color.class), List.of(edited), tempDir);
        String module = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertEquals(1, occurrences(module, "export type " + name + "_"), module);
        assertTrue(module.contains(name + "_ = " + name + " | \"RED\""), module);
        assertFalse(module.contains("EditedColor"), module);
    }

    @Test
    void hiddenIrDeclarationSuppressesAuthoredReplacement(@TempDir Path tempDir) {
        var entry = ClassDeclarationCatalogEntry.of(Builder.class, DECLARATION,
                Set.of(ClassDeclarationFixtures.Imported.class, ClassDeclarationFixtures.Adapted.class));
        var hidden = new TypeDecl(TypeDecl.Kind.CLASS, Builder.class, Builder.class.getName());
        hidden.hidden = true;

        Map<String, String> files = render(snapshot(List.of(entry), List.of()),
                List.of(Builder.class), List.of(hidden), tempDir);

        String targetModule = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertNotNull(targetModule);
        assertFalse(targetModule.contains("export class " + TARGET_NAME), targetModule);
        assertFalse(targetModule.contains("schema<const S"), targetModule);
    }

    @Test
    void explicitImportExcludedByProbeConfigurationFailsWithStableCode(@TempDir Path tempDir) {
        var entry = ClassDeclarationCatalogEntry.of(Builder.class, DECLARATION,
                Set.of(ClassDeclarationFixtures.Imported.class));
        NekoScriptCatalogSnapshot snapshot = snapshot(List.of(entry), List.of());
        ProbeConfig config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(),
                List.of("com.tkisor.nekojs.probe.testfixture"), List.of(), 5, "SMART"));
        ProbeContext context = context(snapshot, List.of(Builder.class), null, config, tempDir);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> new TypeScriptProbeBackend().render(context));
        assertTrue(failure.getMessage().contains("[NEKO-4033]"), failure.getMessage());
    }

    @Test
    void hiddenRequiredImportFailsInsteadOfEmittingAnUnresolvedReference(@TempDir Path tempDir) {
        var entry = ClassDeclarationCatalogEntry.of(Builder.class,
                "export class " + TARGET_NAME + " { value(): $ClassDeclarationFixtures$Imported; }",
                Set.of(ClassDeclarationFixtures.Imported.class));
        var hidden = new TypeDecl(TypeDecl.Kind.CLASS, ClassDeclarationFixtures.Imported.class,
                ClassDeclarationFixtures.Imported.class.getName());
        hidden.hidden = true;
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> render(snapshot(List.of(entry), List.of()),
                        List.of(Builder.class, ClassDeclarationFixtures.Imported.class), List.of(hidden), tempDir));
        assertTrue(failure.getMessage().contains("[NEKO-4033]"), failure.getMessage());
        assertTrue(failure.getMessage().contains(ClassDeclarationFixtures.Imported.class.getName()), failure.getMessage());
    }

    private static Map<String, String> render(NekoScriptCatalogSnapshot snapshot,
                                               List<Class<?>> classes,
                                               List<TypeDecl> ir,
                                               Path tempDir) {
        return render(snapshot, classes, ir, allTypesConfig(), tempDir);
    }

    private static Map<String, String> render(NekoScriptCatalogSnapshot snapshot,
                                               List<Class<?>> classes,
                                               List<TypeDecl> ir,
                                               ProbeConfig config,
                                               Path tempDir) {
        return new TypeScriptProbeBackend().render(context(snapshot, classes, ir, config, tempDir));
    }

    private static ProbeContext context(NekoScriptCatalogSnapshot snapshot,
                                        List<Class<?>> classes,
                                        List<TypeDecl> ir,
                                        ProbeConfig config,
                                        Path tempDir) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tempDir);
        return new ProbeContext.Of(snapshot, classes, config, paths, "typescript",
                paths.gameDir().resolve(".neko_probe/typescript"), ir);
    }

    private static ProbeConfig allTypesConfig() {
        return new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "FULL"));
    }

    private static NekoScriptCatalogSnapshot snapshot(List<ClassDeclarationCatalogEntry> declarations,
                                                       List<AdapterCatalogEntry> adapters) {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), adapters, List.of(), List.of(), List.of(),
                List.of(), List.of(), declarations, List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
    }

    private static NekoScriptCatalogSnapshot legacyEmptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }

    public static final class Builder {
        public String reflectedOnlyMethod() {
            return "reflected";
        }
    }

    public enum Color { RED }
}
