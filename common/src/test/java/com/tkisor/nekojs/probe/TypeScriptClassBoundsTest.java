package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.testfixture.probe.SecondaryBound;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptClassBoundsTest {
    public static class Primary { public int base = 1; }
    public static class Bounded<T extends Primary & SecondaryBound> { public T value; }
    public static class Both extends Primary implements SecondaryBound {
        public String marker() { return "owned"; }
    }
    public interface Replacement { String alternate(); }
    public interface Alternate { int alternateNumber(); }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void secondaryBoundsReachDeclarationsAndTheirDependencyClosure(@TempDir Path directory) throws Exception {
        writeFixture("plain", directory);
        var value = new Bounded<Both>();
        value.value = new Both();
        try (Context runtime = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            runtime.getBindings("js").putMember("bounded", value);
            assertEquals("owned", runtime.eval("js", "bounded.value.marker()").asString());
            assertEquals(1, runtime.eval("js", "bounded.value.base").asInt());
        }
    }

    @Test
    void renamedCanonicalAndAdapterAliasesRetainEveryBound(@TempDir Path directory) throws Exception {
        writeFixture("aliases", directory);
    }

    @Test
    void explicitLegacyConstraintEditsDoNotRestoreOriginalSecondaryBounds(@TempDir Path directory) throws Exception {
        for (String mode : List.of("edited", "unflagged", "replaced", "cleared")) writeFixture(mode, directory.resolve(mode));
    }

    @Test
    void assignedSecondaryBoundsUseTheEffectiveTypeAndDependency(@TempDir Path directory) throws Exception {
        writeFixture("assigned", directory);
    }

    @Test
    void assignedUnionBoundsKeepTheirIntersectionPrecedence(@TempDir Path directory) throws Exception {
        writeFixture("union", directory);
    }

    private static void writeFixture(String mode, Path directory) throws Exception {
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 1, "SMART"));
        List<AdapterCatalogEntry> adapters = mode.equals("plain") ? List.of()
                : List.of(new AdapterCatalogEntry(Bounded.class,
                        List.of(AdapterInputShape.self(), AdapterInputShape.string()),
                        ConversionPrecedence.LOWEST, Optional.empty()));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), adapters,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var reflected = new TypeReflector().reflect(Bounded.class);
        if (mode.equals("aliases")) {
            reflected.renameTo = "RenamedBounded";
            reflected.mutated = true;
        }
        if (mode.equals("edited") || mode.equals("unflagged")) {
            var bound = reflected.typeParams.getFirst().bound;
            bound.ref = TypeReflector.toRef(String.class);
            bound.overridden = mode.equals("edited");
            reflected.mutated = true;
        } else if (mode.equals("replaced")) {
            reflected.typeParams.getFirst().bound = TypeSlot.of(null, TypeReflector.toRef(String.class));
            reflected.mutated = true;
        } else if (mode.equals("cleared")) {
            reflected.typeParams.getFirst().bound = null;
            reflected.mutated = true;
        } else if (mode.equals("assigned")) {
            ProbeAssignTypeEventJS.applyTo(reflected, Map.of(SecondaryBound.class.getName(),
                    TypeReflector.toRef(Replacement.class)));
        } else if (mode.equals("union")) {
            ProbeAssignTypeEventJS.applyTo(reflected, Map.of(SecondaryBound.class.getName(), ApiTypeRef.union(
                    List.of(TypeReflector.toRef(Replacement.class), TypeReflector.toRef(Alternate.class)))));
        }
        var context = new ProbeContext.Of(snapshot, List.of(Bounded.class), config,
                NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve("types"),
                List.of(reflected));
        var files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-class-bounds", mode);
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        String exercise = """
                import type { $TypeScriptClassBoundsTest$Bounded as Bounded } from 'java:com/tkisor/nekojs/probe';
                declare const valid: Bounded<{base: number; marker(): string}>;
                const marker: string = valid.value.marker();
                // @ts-expect-error The second bound is required alongside the first.
                declare const missingSecondary: Bounded<{base: number}>;
                // @ts-expect-error The first bound remains required.
                declare const missingPrimary: Bounded<{marker(): string}>;
                """ + (mode.equals("aliases") ? """
                import type { $TypeScriptClassBoundsTest$Bounded_ as Input,
                    $RenamedBounded as Renamed } from 'java:com/tkisor/nekojs/probe';
                const encoded: Input<{base: number; marker(): string}> = 'encoded';
                declare const renamed: Renamed<{base: number; marker(): string}>;
                const canonical: Bounded<{base: number; marker(): string}> = renamed;
                // @ts-expect-error Adapter aliases retain the second bound.
                declare const invalidInput: Input<{base: number}>;
                """ : "");
        boolean edited = List.of("edited", "unflagged", "replaced", "cleared").contains(mode);
        if (edited) {
            exercise = """
                    import type { $TypeScriptClassBoundsTest$Bounded as Bounded,
                        $TypeScriptClassBoundsTest$Bounded_ as Input } from 'java:com/tkisor/nekojs/probe';
                    declare const bounded: Bounded<string>;
                    const result: string = bounded.value;
                    const encoded: Input<string> = 'encoded';
                    // @ts-expect-error An explicit type argument still controls the value.
                    const wrong: boolean = bounded.value;
                    """ + (mode.equals("cleared") ? "declare const unbounded: Bounded<number>;\n" : """
                    // @ts-expect-error The edited constraint replaces the original Java constraint.
                    declare const invalid: Bounded<number>;
                    """);
        } else if (mode.equals("assigned")) {
            exercise = exercise.replace("marker", "alternate");
        } else if (mode.equals("union")) {
            exercise = """
                    import type { $TypeScriptClassBoundsTest$Bounded as Bounded,
                        $TypeScriptClassBoundsTest$Bounded_ as Input } from 'java:com/tkisor/nekojs/probe';
                    declare const left: Bounded<{base: number; alternate(): string}>;
                    declare const right: Bounded<{base: number; alternateNumber(): number}>;
                    const encoded: Input<{base: number; alternate(): string}> = 'encoded';
                    // @ts-expect-error Every union alternative must still satisfy the first bound.
                    declare const missingPrimary: Bounded<{alternate(): string}>;
                    // @ts-expect-error Adapter aliases retain the same intersection grouping.
                    declare const invalidInput: Input<{alternate(): string}>;
                    """;
        }
        Files.writeString(output.resolve("exercise.ts"), exercise);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"skipLibCheck":false,"noEmit":true,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                "paths":{"java:*": ["./@package/*"],"@special/*":["./@special/*"]}},
                "include":["exercise.ts","@package/**/*.d.ts","@special/**/*.d.ts"]}
                """);
        assertEquals(!edited && !List.of("assigned", "union").contains(mode),
                files.containsKey("@package/com/tkisor/nekojs/testfixture/probe/index.d.ts"), files.keySet().toString());
        String declaration = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        String constraint = "T extends ($TypeScriptClassBoundsTest$Primary) & ($SecondaryBound)";
        if (edited) constraint = mode.equals("cleared") ? "Bounded<T>" : "T extends string";
        else if (mode.equals("assigned")) {
            constraint = "T extends ($TypeScriptClassBoundsTest$Primary) & ($TypeScriptClassBoundsTest$Replacement)";
        } else if (mode.equals("union")) {
            constraint = "T extends ($TypeScriptClassBoundsTest$Primary) & (";
        }
        assertTrue(declaration.contains(constraint), declaration);
    }
}
