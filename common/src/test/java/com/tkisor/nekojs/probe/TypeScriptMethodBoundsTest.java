package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.ir.TypeReflector;
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

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptMethodBoundsTest {
    public static class Primary { public int base = 1; }
    public interface Replacement { String alternate(); }
    public interface Alternate { int alternateNumber(); }
    public static class Both extends Primary implements SecondaryBound {
        public String marker() { return "owned"; }
    }
    public static class Host {
        public <T extends Primary & SecondaryBound> T select(T value) { return value; }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void reflectedMethodBoundsDescribeTheActualHostResult(@TempDir Path directory) throws Exception {
        writeFixture("plain", directory);
        try (Context runtime = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            runtime.getBindings("js").putMember("host", new Host());
            runtime.getBindings("js").putMember("value", new Both());
            assertEquals("owned", runtime.eval("js", "host.select(value).marker()").asString());
            assertEquals(1, runtime.eval("js", "host.select(value).base").asInt());
        }
    }

    @Test
    void assignmentsReachMethodBoundsAndTheirDependencyClosure(@TempDir Path directory) throws Exception {
        writeFixture("assigned", directory);
    }

    @Test
    void editedMethodUnionBoundsKeepTheirIntersectionPrecedence(@TempDir Path directory) throws Exception {
        writeFixture("union", directory);
    }

    private static void writeFixture(String mode, Path directory) throws Exception {
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 1, "SMART"));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var reflected = new TypeReflector().reflect(Host.class);
        if (mode.equals("assigned")) {
            ProbeAssignTypeEventJS.applyTo(reflected, Map.of(SecondaryBound.class.getName(),
                    TypeReflector.toRef(Replacement.class)));
        } else if (mode.equals("union")) {
            var method = reflected.methods.stream().filter(candidate -> candidate.name.equals("select")).findFirst().orElseThrow();
            var bound = method.typeParameterBounds.get("T").get(1);
            bound.ref = ApiTypeRef.union(List.of(TypeReflector.toRef(Replacement.class), TypeReflector.toRef(Alternate.class)));
            bound.overridden = true;
            reflected.mutated = true;
        }
        var context = new ProbeContext.Of(snapshot, List.of(Host.class), config,
                NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve("types"), List.of(reflected));
        var files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-method-bounds", mode);
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        String exercise = """
                import type { $TypeScriptMethodBoundsTest$Host as Host } from 'java:com/tkisor/nekojs/probe';
                declare const host: Host;
                declare const valid: {base: number; marker(): string};
                const result = host.select(valid);
                const marker: string = result.marker();
                // @ts-expect-error The second method bound remains required.
                host.select({base: 1});
                // @ts-expect-error The first method bound remains required.
                host.select({marker() { return 'owned'; }});
                """;
        if (mode.equals("assigned")) exercise = exercise.replace("marker", "alternate");
        else if (mode.equals("union")) {
            exercise = """
                    import type { $TypeScriptMethodBoundsTest$Host as Host } from 'java:com/tkisor/nekojs/probe';
                    declare const host: Host;
                    host.select({base: 1, alternate() { return 'owned'; }});
                    host.select({base: 1, alternateNumber() { return 1; }});
                    // @ts-expect-error The alternate branch cannot bypass the first bound.
                    host.select({alternate() { return 'owned'; }});
                    // @ts-expect-error The intersection also rejects a missing union branch.
                    host.select({base: 1});
                    """;
        }
        Files.writeString(output.resolve("exercise.ts"), exercise);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"skipLibCheck":false,"noEmit":true,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                "paths":{"java:*": ["./@package/*"],"@special/*":["./@special/*"]}},
                "include":["exercise.ts","@package/**/*.d.ts","@special/**/*.d.ts"]}
                """);
        assertEquals(mode.equals("plain"), files.containsKey("@package/com/tkisor/nekojs/testfixture/probe/index.d.ts"), files.keySet().toString());
        String declaration = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        if (!mode.equals("plain")) assertTrue(declaration.contains("select<T extends ($TypeScriptMethodBoundsTest$Primary) & ("), declaration);
    }
}
