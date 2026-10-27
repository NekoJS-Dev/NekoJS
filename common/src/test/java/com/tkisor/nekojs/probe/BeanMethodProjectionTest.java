package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises exact method-to-Bean migrations through the public declaration backend. */
class BeanMethodProjectionTest {
    public static class Parent { public int getX() { return 4; } }
    public static class Child extends Parent {
        public int x() { return 4; }
        public static int x(long value) { return (int) value; }
    }
    public static class Independent extends Parent { public int x() { return 8; } }
    public static class Bounded<T extends Parent> { public T value; }
    private static final List<Class<?>> TYPES = List.of(Parent.class, Child.class, Bounded.class);
    private static final BeanMethodProjectionCatalogEntry RULE = new BeanMethodProjectionCatalogEntry(
            Child.class, "x", "getX");
    private static final String MODULE = "@package/com/tkisor/nekojs/probe/index.d.ts";

    @Test
    void projectsOnlyTheApprovedMethodAndPreservesTheBeanAcrossReuse(@TempDir Path temp) {
        var backend = new TypeScriptProbeBackend();
        var ir = TYPES.stream().map(type -> new TypeReflector().reflect(type)).toList();
        var python = new com.tkisor.nekojs.probe.backend.python.PythonClassRenderer(
                new com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer(java.util.Set.of()));
        var pythonBefore = ir.stream().map(python::render).toList();
        String before = backend.render(context(temp, List.of(), ir)).get(MODULE);
        String after = backend.render(context(temp, List.of(RULE), ir)).get(MODULE);
        assertTrue(before.contains("        x(): number;"), before);
        assertFalse(after.contains("        x(): number;"), after);
        assertTrue(after.contains("get x(): number;") && after.contains("getX(): number;"), after);
        assertTrue(after.contains("static x(value: number): number;"), after);
        assertEquals(before, backend.render(context(temp, List.of(), ir)).get(MODULE));
        assertEquals(pythonBefore, ir.stream().map(python::render).toList());
        assertTrue(ir.stream().flatMap(type -> type.methods.stream())
                .anyMatch(method -> method.name.equals("x") && !method.hidden && !method.isStatic));
    }

    @Test
    void generatesStrictConsumerWithMigrationAndBoundChecks(@TempDir Path temp) throws Exception {
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(RULE), null));
        Path output = Path.of("build/probe-ts-bean-method-projection");
        if (Files.exists(output)) {
            Path build = Path.of("build").toRealPath();
            assertTrue(output.toRealPath().startsWith(build) && !output.toRealPath().equals(build));
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        for (var entry : files.entrySet()) {
            Path path = output.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $BeanMethodProjectionTest$Child as Child } from 'java:com/tkisor/nekojs/probe';
                import type { $BeanMethodProjectionTest$Parent as Parent, $BeanMethodProjectionTest$Bounded as Bounded } from 'java:com/tkisor/nekojs/probe';
                declare const child: Child;
                const parent: Parent = child;
                const bounded: Bounded<Child> = {value: child};
                const property: number = child.x;
                const getter: number = child.getX();
                const staticResult: number = Child.x(4);
                // @ts-expect-error The approved redundant method spelling is migrated.
                child.x();
                // @ts-expect-error The preserved property is a number, not a string.
                const wrong: string = child.x;
                // @ts-expect-error The original parent bound remains enforced.
                type Invalid = Bounded<number>;
                """);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"hidden-getter", "renamed-getter", "getter-result", "hidden-parent",
            "renamed-method", "method-result", "method-edit", "synthetic-getter", "synthetic-field"})
    void preservesVisibleIrEditsInsteadOfRecoveringTheGetterFromReflection(String change, @TempDir Path temp) {
        var ir = TYPES.stream().map(type -> new TypeReflector().reflect(type)).toList();
        TypeDecl parent = ir.getFirst();
        TypeDecl child = ir.get(1);
        var getter = parent.methods.stream().filter(method -> method.name.equals("getX")).findFirst().orElseThrow();
        var method = child.methods.stream().filter(candidate -> candidate.name.equals("x") && !candidate.isStatic).findFirst().orElseThrow();
        switch (change) {
            case "hidden-getter" -> getter.hidden = true;
            case "renamed-getter" -> getter.renameTo = "coordinate";
            case "getter-result" -> { getter.returnType.ref = com.tkisor.nekojs.api.surface.ApiTypeRef.primitive("string"); getter.returnType.overridden = true; }
            case "hidden-parent" -> parent.hidden = true;
            case "renamed-method" -> method.renameTo = "coordinate";
            case "method-result" -> { method.returnType.ref = com.tkisor.nekojs.api.surface.ApiTypeRef.primitive("string"); method.returnType.overridden = true; }
            case "method-edit" -> method.explicitlyEdited = true;
            case "synthetic-getter" -> {
                var synthetic = new com.tkisor.nekojs.probe.ir.MethodDecl("getX");
                synthetic.isGetter = true;
                synthetic.property = "x";
                synthetic.returnType = new com.tkisor.nekojs.probe.ir.TypeSlot(null,
                        com.tkisor.nekojs.api.surface.ApiTypeRef.primitive("string"));
                child.methods.add(synthetic);
            }
            case "synthetic-field" -> child.fields.add(new com.tkisor.nekojs.probe.ir.FieldDecl("x",
                    new com.tkisor.nekojs.probe.ir.TypeSlot(null,
                            com.tkisor.nekojs.api.surface.ApiTypeRef.primitive("number"))));
            default -> fail(change);
        }
        parent.mutated = true;
        child.mutated = true;
        var backend = new TypeScriptProbeBackend();
        assertEquals(backend.render(context(temp, List.of(), ir)).get(MODULE),
                backend.render(context(temp, List.of(RULE), ir)).get(MODULE), change);
    }

    @Test
    void doesNotProjectUnrelatedClassesOrTheirMethods(@TempDir Path temp) {
        var ir = List.of(Parent.class, Child.class, Independent.class).stream()
                .map(type -> new TypeReflector().reflect(type)).toList();
        String output = new TypeScriptProbeBackend().render(context(temp, List.of(RULE), ir)).get(MODULE);
        int independent = output.indexOf("export class $BeanMethodProjectionTest$Independent");
        assertTrue(independent >= 0 && output.substring(independent).contains("x(): number;"), output);
    }

    @Test
    void preservesAuthoredBodiesAndDoesNotInferTheirGetterContract(@TempDir Path temp) {
        for (var authored : List.of(
                com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry.of(Child.class,
                        "export class $BeanMethodProjectionTest$Child { authored(): string; }"),
                com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry.of(Parent.class,
                        "export class $BeanMethodProjectionTest$Parent { readonly x: number; getX(): number; }"))) {
            var backend = new TypeScriptProbeBackend();
            assertEquals(backend.render(context(temp, List.of(), null, List.of(authored))).get(MODULE),
                    backend.render(context(temp, List.of(RULE), null, List.of(authored))).get(MODULE));
        }
    }

    private static ProbeContext context(Path temp, List<BeanMethodProjectionCatalogEntry> rules, List<TypeDecl> ir) {
        return context(temp, rules, ir, List.of());
    }

    private static ProbeContext context(Path temp, List<BeanMethodProjectionCatalogEntry> rules, List<TypeDecl> ir,
                                       List<com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry> authored) {
        TestPlatformInit.ensureInitialized();
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), authored, List.of(), List.of(),
                List.of(), null, Map.of(), List.of(), List.of(), rules);
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "FULL"));
        var classes = new java.util.ArrayList<Class<?>>(TYPES);
        if (ir != null) ir.forEach(declaration -> classes.add(declaration.sourceClass));
        return new ProbeContext.Of(snapshot, classes.stream().distinct().toList(), config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"), ir);
    }
}
