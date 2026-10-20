package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManualDeclarationReferencesTest {
    public static class Dependency { public int count; }
    public static class Box<T> { public T value; }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void explicitManualDependencyCompletesDeclarationsWithoutChangingThePrimaryScan(@TempDir Path temp) throws Exception {
        var entry = ManualDeclarationCatalogEntry.of("test:dependency", "type ManualResult = ManualDependency; "
                        + "declare function manualAccept(value: ManualInput): ManualResult;",
                "A host value referenced by an authored declaration.", List.of())
                .withReferences(Map.of("ManualDependency", ManualDeclarationCatalogEntry.TypeReference.host(Dependency.class),
                        "ManualInput", ManualDeclarationCatalogEntry.TypeReference.input(Dependency.class)));
        var context = context(temp, List.of(entry), List.of(), List.of());
        assertTrue(ProbeCoordinator.collectClasses(context.snapshot(), context.config()).isEmpty());
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        assertNotNull(files.get("@manual/references.d.ts"), "Explicit dependencies must have global type aliases");
        assertTrue(files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts").contains("count: number;"));
        Path output = Path.of("build/probe-ts-manual-references");
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@manual/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                declare const value: ManualResult;
                const count: number = value.count;
                const converted: ManualResult = manualAccept('value');
                manualAccept(value);
                // @ts-expect-error The registered input conversion does not accept booleans.
                manualAccept(true);
                // @ts-expect-error The manual dependency retains its real field type.
                const invalid: string = value.count;
                // @ts-expect-error Completing a dependency does not create arbitrary members.
                value.missing;
                """);
    }

    @Test
    void excludedAndHiddenExplicitDependenciesRejectTheAuthoredDeclaration(@TempDir Path temp) {
        var entry = entry("ManualDependency", Dependency.class);
        var excluded = context(temp, List.of(entry), List.of(Dependency.class.getPackageName()), List.of());
        var failure = assertThrows(IllegalArgumentException.class, () -> new TypeScriptProbeBackend().render(excluded));
        assertTrue(failure.getMessage().contains("[NEKO-4033]"), failure.getMessage());
        TypeDecl hidden = new TypeReflector().reflect(Dependency.class);
        hidden.hidden = true;
        var context = context(temp, List.of(entry), List.of(), List.of(hidden));
        failure = assertThrows(IllegalArgumentException.class, () -> new TypeScriptProbeBackend().render(context));
        assertTrue(failure.getMessage().contains("[NEKO-4033]"), failure.getMessage());
    }

    @Test
    void explicitDependenciesKeepEditedMembersAndCanonicalIdentityAfterRename(@TempDir Path temp) {
        TypeDecl edited = new TypeReflector().reflect(Dependency.class);
        edited.renameTo = "RenamedDependency";
        edited.fields.getFirst().type.ref = ApiTypeRef.primitive("string");
        edited.fields.getFirst().type.overridden = true;
        edited.mutated = true;
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(entry("ManualDependency", Dependency.class)),
                List.of(), List.of(edited)));
        String module = files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts");
        assertTrue(module.contains("count: string;"), module);
        assertTrue(module.contains("class $RenamedDependency"), module);
        assertTrue(files.get("@manual/references.d.ts").contains(".$ManualDeclarationReferencesTest$Dependency;"));
    }

    @Test
    void conflictingGlobalReferencesFailInsteadOfDependingOnPluginOrder(@TempDir Path temp) {
        var context = context(temp, List.of(entry("ManualDependency", Dependency.class),
                entry("ManualDependency", String.class)), List.of(), List.of());
        var failure = assertThrows(IllegalArgumentException.class, () -> new TypeScriptProbeBackend().render(context));
        assertTrue(failure.getMessage().contains("[NEKO-4051]"), failure.getMessage());
    }

    @Test
    void rawGenericArrayReferencesCompleteTheirElementInsteadOfDeclaringAnArrayClass(@TempDir Path temp) throws Exception {
        var files = new TypeScriptProbeBackend().render(context(temp,
                List.of(entry("ManualBoxes", Box[].class)), List.of(), List.of()));
        String module = files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts");
        assertNotNull(module, "The referenced array element needs its actual Java package module");
        assertTrue(module.contains("class $ManualDeclarationReferencesTest$Box<T>"), module);
        assertTrue(files.get("@manual/references.d.ts").contains(".$ManualDeclarationReferencesTest$Box<any>[];"));
        Path output = Path.of("build/probe-ts-manual-array-references");
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@manual/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                declare const boxes: ManualBoxes;
                const count: number = boxes.length;
                const contents: unknown = boxes[0].value;
                // @ts-expect-error Raw generic arguments do not erase the container's members.
                boxes[0].missing;
                // @ts-expect-error A Java array reference remains an array.
                const notAnArray: number = boxes;
                """);
    }

    private static ManualDeclarationCatalogEntry entry(String name, Class<?> target) {
        return ManualDeclarationCatalogEntry.of("test:" + target.getName(), "type ManualResult = " + name + ";",
                "A required manual type reference.", List.of())
                .withReferences(Map.of(name, ManualDeclarationCatalogEntry.TypeReference.host(target)));
    }

    private static ProbeContext context(Path temp, List<ManualDeclarationCatalogEntry> entries,
                                        List<String> excludes, List<TypeDecl> ir) {
        var adapter = new AdapterCatalogEntry(Dependency.class, List.of(AdapterInputShape.self(), AdapterInputShape.string()),
                ConversionPrecedence.LOW, Optional.empty());
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(adapter), List.of(),
                List.of(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), excludes, List.of(), 1, "SMART"));
        return new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(temp), "typescript",
                temp.resolve("typescript"), ir);
    }
}
