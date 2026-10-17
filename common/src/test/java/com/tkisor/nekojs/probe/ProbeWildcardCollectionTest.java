package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProbeWildcardCollectionTest {
    public static class Box<T> { public T value; }
    public static class Value { public String label; public BeyondDepth next; }
    public static class BeyondDepth {}
    public static class LowerOnly {}
    public static class Root { public Box<? extends Value[][]> values; }
    public static class FilteredRoot {
        public Box<? extends java.net.URI> outsideIncludes;
        public Box<? extends java.awt.Color> excluded;
        public Box<? super LowerOnly> lowerOnly;
        public Box<?> unbounded;
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void ordinaryWildcardUpperBoundsUseTheSignatureDepth() {
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot(Root.class), config());
        assertTrue(collected.contains(Value.class), collected.toString());
        assertTrue(collected.contains(Box.class), collected.toString());
        assertFalse(collected.contains(BeyondDepth.class), "Upper-bound members still obey maxDepth");
    }

    @Test
    void ordinaryWildcardsRetainIncludesExcludesAndUpperOnlyProjection() {
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot(FilteredRoot.class), config());
        assertFalse(collected.contains(java.net.URI.class));
        assertFalse(collected.contains(java.awt.Color.class));
        assertFalse(collected.contains(LowerOnly.class), "An ordinary lower wildcard renders as any");
        assertFalse(collected.contains(Object.class));
    }

    @Test
    void realCollectedDeclarationsResolveNestedUpperBounds(@TempDir Path temp) throws Exception {
        var snapshot = snapshot(Root.class);
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 2, "SMART"));
        List<Class<?>> collected = List.copyOf(ProbeCoordinator.collectClasses(snapshot, config));
        var paths = NekoJSPaths.fromGameDir(temp);
        var context = new ProbeContext.Of(snapshot, collected, config, paths, "typescript",
                temp.resolve("typescript"));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-wildcard-collection");
        for (var file : files.entrySet()) {
            Path destination = output.resolve(file.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $ProbeWildcardCollectionTest$Root as Root } from 'java:com/tkisor/nekojs/probe';
                declare const root: Root;
                const label: string = root.values.value[0][0].label;
                // @ts-expect-error The wildcard upper bound keeps its member types.
                const invalid: number = root.values.value[0][0].label;
                // @ts-expect-error The nested array shape remains precise.
                const missingDimension: string = root.values.value[0].label;
                """);
        String declarations = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertTrue(declarations.contains("export class $ProbeWildcardCollectionTest$Value"), declarations);
    }

    private static ProbeConfig config() {
        return new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe", "java.awt"), List.of(), List.of("java.awt"),
                List.of(), 1, "SMART"));
    }

    private static NekoScriptCatalogSnapshot snapshot(Class<?> root) {
        return new NekoScriptCatalogSnapshot(List.of(),
                List.of(BindingCatalogEntry.of("Root", ScriptType.SERVER, root, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
