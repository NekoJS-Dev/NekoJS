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

/**
 * Output use sites of {@code Optional<? extends Bound>} render a capture-safe view instead of the
 * flattened invariant {@code $Optional<$Bound>}, so Java-legal covariant overrides
 * (interface {@code Optional<? extends B>}, implementation {@code Optional<Sub>}) stay assignable
 * while the interface result keeps the upper bound and the implementation keeps its narrow view.
 */
class ProbeWildcardOptionalCaptureTest {
    public static class ConstDesc { public String label; }
    public static class DynamicDesc extends ConstDesc { public int code; }
    public interface ConstableLike { java.util.Optional<? extends ConstDesc> describeConstable(); }
    public static class BooleanLike implements ConstableLike {
        @Override
        public java.util.Optional<DynamicDesc> describeConstable() { return java.util.Optional.empty(); }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void wildcardOptionalOutputRendersCaptureViewAndKeepsNarrowImplementation(@TempDir Path temp) throws Exception {
        var snapshot = snapshot();
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of("java.time"), List.of(), 3, "SMART"));
        List<Class<?>> collected = List.copyOf(ProbeCoordinator.collectClasses(snapshot, config));
        var paths = NekoJSPaths.fromGameDir(temp);
        var context = new ProbeContext.Of(snapshot, collected, config, paths, "typescript",
                temp.resolve("typescript"));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-wildcard-optional");
        if (Files.exists(output)) {
            try (var walk = Files.walk(output)) {
                for (var entry : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(entry);
                }
            }
        }
        for (var file : files.entrySet()) {
            Path destination = output.resolve(file.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"],"java:java/time": ["java-time-ambient.d.ts"]}},"include":["@package/**/*.d.ts","java-time-ambient.d.ts","exercise.ts"]}
                """);
        // The fixture closure reaches java.lang (Thread.sleep/$Duration, Date.from/$Instant); the
        // java.time tree itself carries the unrelated pre-existing chrono family, so this focused
        // group resolves those two imported names to opaque nominal classes instead of pulling the
        // whole package. The real java.time diagnostics stay counted by the ten-entrypoint baseline.
        Files.writeString(output.resolve("java-time-ambient.d.ts"), """
                declare class $Duration { static toString(): string; }
                declare class $Instant { static toString(): string; }
                export { $Duration, $Instant };
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type {
                    $ProbeWildcardOptionalCaptureTest$ConstableLike as ConstableLike,
                    $ProbeWildcardOptionalCaptureTest$ConstDesc as ConstDesc,
                    $ProbeWildcardOptionalCaptureTest$DynamicDesc as DynamicDesc,
                    $ProbeWildcardOptionalCaptureTest$BooleanLike,
                } from 'java:com/tkisor/nekojs/probe';
                import type { $Optional } from 'java:java/util';
                import type { $Predicate_, $Supplier_ } from 'java:java/util/function';

                declare const impl: $ProbeWildcardOptionalCaptureTest$BooleanLike;

                // The Java-legal covariant override stays assignable to the interface.
                const asInterface: ConstableLike = impl;

                // The interface result keeps the upper bound through the capture view.
                const upper: ConstDesc = asInterface.describeConstable().get();
                const label: string = upper.label;
                // @ts-expect-error The upper-bound view does not expose the narrow member.
                upper.code;

                // The stream projection recurses one capture level: terminal results read as the bound.
                const found: ConstDesc = asInterface.describeConstable().stream().findAny().get();
                const counted: number = asInterface.describeConstable().stream().count();
                // @ts-expect-error The truncated capture level does not re-enter stream.
                asInterface.describeConstable().stream().findAny().stream();

                // The concrete implementation view keeps its narrow result.
                const narrow: DynamicDesc = impl.describeConstable().get();
                const code: number = narrow.code;

                // SAM input directions on the concrete view keep accepting correct and
                // rejecting unrelated payloads.
                declare const goodSupplier: $Supplier_<$Optional<DynamicDesc>, $Optional<DynamicDesc>>;
                const supplied: $Optional<DynamicDesc> = impl.describeConstable().or(goodSupplier);
                declare const goodPredicate: $Predicate_<DynamicDesc, DynamicDesc, boolean>;
                const filtered: $Optional<DynamicDesc> = impl.describeConstable().filter(goodPredicate);
                declare const badSupplier: $Supplier_<$Optional<string>, $Optional<string>>;
                // @ts-expect-error An unrelated supplier payload is rejected.
                impl.describeConstable().or(badSupplier);
                // The converted-callable branch deliberately accepts any host element; the callback
                // result kind stays checked.
                declare const badPredicate: $Predicate_<ConstDesc, ConstDesc, string>;
                // @ts-expect-error A predicate returning the wrong kind is rejected.
                impl.describeConstable().filter(badPredicate);
                """);
        String declarations = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        String interfaceBlock = blockOf(declarations, "export interface $ProbeWildcardOptionalCaptureTest$ConstableLike");
        assertTrue(interfaceBlock.contains("describeConstable(): {"),
                "interface return renders the capture view: " + interfaceBlock);
        assertTrue(interfaceBlock.contains("get(): $ProbeWildcardOptionalCaptureTest$ConstDesc;"),
                "capture view keeps the upper bound: " + interfaceBlock);
        assertFalse(interfaceBlock.contains("$Optional<$ProbeWildcardOptionalCaptureTest$ConstDesc>"),
                "flattened invariant optional no longer appears on the interface: " + interfaceBlock);
        String implBlock = blockOf(declarations, "export class $ProbeWildcardOptionalCaptureTest$BooleanLike");
        assertTrue(implBlock.contains(
                "describeConstable(): $Optional<$ProbeWildcardOptionalCaptureTest$DynamicDesc>;"),
                "implementation keeps its narrow optional: " + implBlock);
    }

    private static String blockOf(String declarations, String header) {
        int start = declarations.indexOf(header);
        assertTrue(start >= 0, header + " missing");
        int end = declarations.indexOf("\n    }", start);
        return declarations.substring(start, end);
    }

    private static NekoScriptCatalogSnapshot snapshot() {
        return new NekoScriptCatalogSnapshot(List.of(),
                List.of(BindingCatalogEntry.of("ConstableLike", ScriptType.SERVER, ConstableLike.class, false),
                        BindingCatalogEntry.of("BooleanLike", ScriptType.SERVER, BooleanLike.class, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
