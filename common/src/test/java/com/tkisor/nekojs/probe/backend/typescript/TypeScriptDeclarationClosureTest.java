package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.testfixture.closure.ClosureTypes;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptDeclarationClosureTest {
    public static class Root { public ClosureTypes.Leaf leaf; }
    public static class DispatchKey { public int value; }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void realReferenceClosureCompletesHeritageWithoutChangingThePrimaryScan(@TempDir Path temp) throws Exception {
        ProbeContext context = context(temp, List.of(), List.of(), List.of());
        Set<Class<?>> primary = ProbeCoordinator.collectClasses(context.snapshot(), context.config());
        assertEquals(Set.of(Root.class), primary);
        IndexFileGenerator generator = generator();
        var closure = new TypeScriptDeclarationClosure(context, generator);
        var classes = new LinkedHashSet<>(Set.of(Root.class.getName()));
        closure.expand(classes);
        assertEquals(Set.of(Root.class.getName(), ClosureTypes.Leaf.class.getName(),
                ClosureTypes.Parent.class.getName(), ClosureTypes.Child.class.getName()), classes);
        assertEquals(List.of(Root.class.getName(), ClosureTypes.Leaf.class.getName(),
                ClosureTypes.Child.class.getName(), ClosureTypes.Parent.class.getName()), List.copyOf(classes));
        assertTrue(closure.unavailable().isEmpty(), closure.unavailable().toString());
        assertEquals(primary, ProbeCoordinator.collectClasses(context.snapshot(), context.config()));
        Map<String, String> production = new TypeScriptProbeBackend().render(context);
        var report = com.google.gson.JsonParser.parseString(production.get("@special/dependencies.json")).getAsJsonObject();
        assertTrue(report.get("complete").getAsBoolean());
        assertEquals(4, report.get("referencedClassCount").getAsInt());
        Path output = Path.of("build/probe-ts-dependency-closure");
        for (var entry : production.entrySet()) {
            Path file = output.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptDeclarationClosureTest$Root as Root } from 'java:com/tkisor/nekojs/probe/backend/typescript';
                declare const root: Root;
                const inherited: number = root.leaf.token;
                const nested: string = root.leaf.nested.text;
                // @ts-expect-error The completed dependency retains real inherited members.
                const wrongParent: string = root.leaf.token;
                // @ts-expect-error The completed member graph is not an empty or any declaration.
                root.leaf.nested.missing;
                """);
    }

    @Test
    void emittedBindingRootsOutsideThePrimaryAllowlistStillHaveDeclarations(@TempDir Path temp) {
        ProbeContext original = context(temp, List.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), List.of(), List.of(), 1, "SMART"));
        Set<Class<?>> primary = ProbeCoordinator.collectClasses(original.snapshot(), config);
        assertTrue(primary.isEmpty());
        var context = new ProbeContext.Of(original.snapshot(), List.copyOf(primary), config, original.paths(),
                "typescript", temp.resolve("typescript"), List.of());
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        assertTrue(files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts")
                .contains("class $TypeScriptDeclarationClosureTest$Root"));
        var report = com.google.gson.JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
        assertTrue(report.get("complete").getAsBoolean());
        assertEquals(0, report.get("primaryClassCount").getAsInt());
        assertEquals(4, report.get("referencedClassCount").getAsInt());
    }

    @Test
    void emittedEventAndDispatchRootsOutsideThePrimaryAllowlistHaveDeclarations(@TempDir Path temp) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(com.tkisor.nekojs.api.catalog.EventCatalogEntry.of(
                "ClosureEvents", "visit", ScriptType.SERVER, Root.class, DispatchKey.class, false, true)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), List.of(), List.of(), 1, "SMART"));
        assertTrue(ProbeCoordinator.collectClasses(snapshot, config).isEmpty());
        var context = new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"), List.of());
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String module = files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts");
        assertTrue(module.contains("class $TypeScriptDeclarationClosureTest$Root"));
        assertTrue(module.contains("class $TypeScriptDeclarationClosureTest$DispatchKey"));
        var report = com.google.gson.JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
        assertTrue(report.get("complete").getAsBoolean());
        assertEquals(0, report.get("primaryClassCount").getAsInt());
        assertEquals(5, report.get("referencedClassCount").getAsInt());
    }

    @Test
    void excludedAndHiddenDependenciesRemainUnavailable(@TempDir Path temp) {
        var classes = new LinkedHashSet<>(Set.of(Root.class.getName()));
        var generator = generator();
        var excluded = new TypeScriptDeclarationClosure(context(temp,
                List.of(ClosureTypes.class.getPackageName()), List.of(), List.of()), generator);
        excluded.expand(classes);
        assertEquals("excluded", excluded.unavailable().get(ClosureTypes.Leaf.class.getName()));
        assertFalse(generator.hasDeclaration(ClosureTypes.Leaf.class.getName()));
        var report = com.google.gson.JsonParser.parseString(new TypeScriptProbeBackend().render(context(temp,
                List.of(ClosureTypes.class.getPackageName()), List.of(), List.of()))
                .get("@special/dependencies.json")).getAsJsonObject();
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("excluded", report.getAsJsonObject("unavailable").get(ClosureTypes.Leaf.class.getName()).getAsString());
        TypeDecl hidden = new TypeReflector().reflect(ClosureTypes.Leaf.class);
        hidden.hidden = true;
        var hiddenClosure = new TypeScriptDeclarationClosure(context(temp, List.of(), List.of(hidden), List.of()), generator());
        hiddenClosure.expand(new LinkedHashSet<>(Set.of(Root.class.getName())));
        assertEquals("hidden", hiddenClosure.unavailable().get(ClosureTypes.Leaf.class.getName()));
    }

    @Test
    void existingEditedIrIsNeverReplacedByReflection(@TempDir Path temp) {
        TypeDecl root = new TypeReflector().reflect(Root.class);
        var slot = root.fields.getFirst().type;
        slot.ref = ApiTypeRef.primitive("string");
        slot.overridden = true;
        root.mutated = true;
        var generator = generator();
        var closure = new TypeScriptDeclarationClosure(context(temp, List.of(), List.of(root), List.of()), generator);
        var classes = new LinkedHashSet<>(Set.of(Root.class.getName()));
        closure.expand(classes);
        assertTrue(generator.generate(Root.class.getPackageName(),
                List.of("TypeScriptDeclarationClosureTest$Root"), List.of(), classes).contains("leaf: string;"));
        assertTrue(slot.overridden);
        assertEquals(ApiTypeRef.primitive("string"), slot.ref);
    }

    @Test
    void dependencyReplacementsBypassTheirOriginalReflectionGraph(@TempDir Path temp) {
        var replacement = ClassDeclarationCatalogEntry.of(ClosureTypes.Leaf.class,
                "export class $ClosureTypes$Leaf { count: number; }", Set.of());
        var classes = new LinkedHashSet<>(Set.of(Root.class.getName()));
        var generator = generator();
        var closure = new TypeScriptDeclarationClosure(context(temp, List.of(), List.of(), List.of(replacement)), generator);
        closure.expand(classes);
        assertTrue(generator.hasDeclaration(ClosureTypes.Leaf.class.getName()));
        assertFalse(classes.contains(ClosureTypes.Parent.class.getName()));
        assertFalse(classes.contains(ClosureTypes.Child.class.getName()));
        assertTrue(closure.unavailable().isEmpty());
    }

    private static IndexFileGenerator generator() {
        var aliases = new TypeAliasRegistry();
        return new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
    }

    @Test
    void samePackageAdapterReferencesAreDependenciesEvenWithoutImports() {
        var adapter = new AdapterAliasGenerator(new TypeAliasRegistry());
        adapter.prepare(List.of(new com.tkisor.nekojs.api.catalog.AdapterCatalogEntry(ClosureTypes.Leaf.class,
                List.of(com.tkisor.nekojs.api.AdapterInputShape.self(),
                        com.tkisor.nekojs.api.AdapterInputShape.host(ClosureTypes.Child.class)),
                com.tkisor.nekojs.api.data.ConversionPrecedence.LOW, java.util.Optional.empty())),
                Set.of(ClosureTypes.Leaf.class.getName()));
        assertTrue(adapter.hostImports().contains(ClosureTypes.Child.class.getName()));
        assertFalse(adapter.getAlias(ClosureTypes.Leaf.class.getName()).importFqns().contains(ClosureTypes.Child.class.getName()));
    }

    @Test
    void libraryReferenceGraphIsFiniteAndMeasured(@TempDir Path temp) throws Exception {
        var classes = new LinkedHashSet<>(Set.of(java.util.List.class.getName()));
        var closure = new TypeScriptDeclarationClosure(context(temp, List.of(), List.of(), List.of()), generator());
        long start = System.nanoTime();
        closure.expand(classes);
        assertTrue(classes.contains(java.util.Collection.class.getName()));
        assertTrue(classes.contains(java.util.Iterator.class.getName()));
        Path output = Path.of("build/probe-ts-dependency-closure");
        Files.createDirectories(output);
        Files.writeString(output.resolve("prototype-jdk.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create().toJson(Map.of("seed", java.util.List.class.getName(), "classCount", classes.size(),
                        "elapsedMs", (System.nanoTime() - start) / 1_000_000,
                        "unavailable", closure.unavailable(), "classes", new java.util.TreeSet<>(classes))) + "\n");
    }

    private static ProbeContext context(Path temp, List<String> excludes, List<TypeDecl> ir,
                                         List<ClassDeclarationCatalogEntry> replacements) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(),
                List.of(BindingCatalogEntry.of("Root", ScriptType.SERVER, Root.class, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), replacements,
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(Root.class.getPackageName()), List.of(), excludes, List.of(), 1, "SMART"));
        var paths = NekoJSPaths.fromGameDir(temp);
        return new ProbeContext.Of(snapshot, List.of(Root.class), config, paths, "typescript",
                temp.resolve("typescript"), ir);
    }
}
