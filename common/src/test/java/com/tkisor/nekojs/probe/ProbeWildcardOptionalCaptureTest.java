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
 * Output use sites of {@code Optional<? extends Bound>} reference a member-driven capture view
 * ({@code $OptionalView<B>}/{@code $StreamView<B>}) instead of the flattened invariant
 * {@code $Optional<$Bound>}, so Java-legal covariant overrides stay assignable while the interface
 * result keeps the full public instance surface of {@code java.util.Optional} (including Bean
 * properties and recursive {@code stream()} navigation). Members whose capture semantics have no
 * provably compatible TypeScript shape are excluded by documented rule (or/reduce/sorted/collect/
 * toList/spliterator) and asserted absent here.
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
                import type { $Optional, $OptionalView } from 'java:java/util';
                import type { $Predicate_, $Supplier_ } from 'java:java/util/function';

                declare const impl: $ProbeWildcardOptionalCaptureTest$BooleanLike;

                // The Java-legal covariant override stays assignable to the interface.
                const asInterface: ConstableLike = impl;

                // The interface result keeps the upper bound through the capture view.
                const captureResult = asInterface.describeConstable();
                const explicit: $OptionalView<ConstDesc> = captureResult;
                const upper: ConstDesc = captureResult.get();
                const label: string = upper.label;
                // @ts-expect-error The upper-bound view does not expose the narrow member.
                upper.code;
                // @ts-expect-error A wrong element kind cannot satisfy the bound.
                const wrongElement: number = captureResult.get();

                // The public instance surface of Optional stays available on the capture view.
                const thrown: ConstDesc = captureResult.orElseThrow();
                const present: boolean = captureResult.present;
                const empty: boolean = captureResult.empty;
                const filtered = captureResult.filter(() => true);
                const orElse: ConstDesc = captureResult.orElse(upper);
                // The SAM alias union does not infer method type variables from callback results (the
                // concrete $Optional surface has the same limitation), so pass them explicitly.
                const mapped = captureResult.map<string>((d: ConstDesc) => d.label);
                const mappedLabel: string = mapped.get();
                declare const otherOpt: $Optional<ConstDesc>;
                const flatMapped = captureResult.flatMap<ConstDesc>((d: ConstDesc): $Optional<ConstDesc> => otherOpt);
                const flatValue: ConstDesc = flatMapped.get();

                // stream() navigation recurses through the terminal optionals.
                const counted: number = captureResult.stream().findAny().stream().count();
                const deep: ConstDesc = captureResult.stream().findAny().stream().findFirst().get();
                const chained = captureResult.stream().filter(() => true).map((d: ConstDesc) => d.label).count();
                const listed: ConstDesc = captureResult.stream().iterator().next();
                const maxed = captureResult.stream().max(null as never);

                // Rule 1: or(Supplier<Optional<? extends T>>) nests the capture container in an input
                // position; the SAM host branch compares the narrowed element in both bivariant
                // directions, so no parameter shape is provably compatible. It stays on the concrete
                // implementations only.
                // @ts-expect-error or is excluded from the capture view by rule 1.
                captureResult.or;
                // Rule 2: overload sets with bound-parameterized functional aliases pair strictly
                // (reduce/sorted/collect); rule 3 excludes invariant container returns (toList,
                // spliterator). They stay available on concrete streams.
                // @ts-expect-error reduce is excluded from the stream capture view by rule 2.
                captureResult.stream().reduce;
                // @ts-expect-error sorted is excluded from the stream capture view by rule 2.
                captureResult.stream().sorted;
                // @ts-expect-error collect is excluded from the stream capture view by rule 2.
                captureResult.stream().collect;
                // @ts-expect-error toList is excluded from the stream capture view by rule 3.
                captureResult.stream().toList;
                // @ts-expect-error spliterator is excluded from the stream capture view by rule 3.
                captureResult.stream().spliterator;

                // The concrete implementation view keeps its narrow result and full container surface.
                const narrow: DynamicDesc = impl.describeConstable().get();
                const code: number = narrow.code;
                declare const goodSupplier: $Supplier_<$Optional<DynamicDesc>, $Optional<DynamicDesc>>;
                const supplied: $Optional<DynamicDesc> = impl.describeConstable().or(goodSupplier);
                declare const goodPredicate: $Predicate_<DynamicDesc, DynamicDesc, boolean>;
                const implFiltered: $Optional<DynamicDesc> = impl.describeConstable().filter(goodPredicate);
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
        String utilFile = files.get("@package/java/util/index.d.ts");
        String streamFile = files.get("@package/java/util/stream/index.d.ts");

        String interfaceBlock = blockOf(declarations, "export interface $ProbeWildcardOptionalCaptureTest$ConstableLike");
        assertTrue(interfaceBlock.contains(
                "describeConstable(): $OptionalView<$ProbeWildcardOptionalCaptureTest$ConstDesc>;"),
                "interface return references the capture view: " + interfaceBlock);
        assertFalse(interfaceBlock.contains("$Optional<$ProbeWildcardOptionalCaptureTest$ConstDesc>"),
                "flattened invariant optional no longer appears on the interface: " + interfaceBlock);
        String implBlock = blockOf(declarations, "export class $ProbeWildcardOptionalCaptureTest$BooleanLike");
        assertTrue(implBlock.contains(
                "describeConstable(): $Optional<$ProbeWildcardOptionalCaptureTest$DynamicDesc>;"),
                "implementation keeps its narrow optional: " + implBlock);

        // The view derives from the rendered instance surface of java.util.Optional.
        String optionalView = blockOf(utilFile, "export interface $OptionalView");
        assertTrue(optionalView.contains("orElseThrow(): B;"), "orElseThrow stays callable: " + optionalView);
        assertTrue(optionalView.contains("get present(): boolean;"), "Bean property stays readable: " + optionalView);
        assertTrue(optionalView.contains("get empty(): boolean;"), "Bean property stays readable: " + optionalView);
        assertTrue(optionalView.contains("filter(arg0: $Predicate_<B, B, boolean>): $OptionalView<B>;"),
                "filter keeps its bound predicate and re-enters the view: " + optionalView);
        assertTrue(optionalView.contains(
                "flatMap<U>(arg0: $Function_<B, $Optional<U>, B, $Optional<U>>): $OptionalView<U>;"),
                "flatMap keeps the concrete container in parameter position: " + optionalView);
        assertTrue(optionalView.contains("stream(): $StreamView<B>;"),
                "stream navigates into the stream view: " + optionalView);
        assertFalse(optionalView.contains(" or(arg0"), "or is excluded by rule 1: " + optionalView);
        assertTrue(utilFile.contains("$StreamView") && utilFile.contains("java:java/util/stream"),
                "java/util imports the stream view it references");

        String streamView = blockOf(streamFile, "export interface $StreamView");
        assertTrue(streamView.contains("findAny(): $OptionalView<B>;"),
                "terminal optionals re-enter the optional view: " + streamView);
        assertTrue(streamView.contains(
                "flatMap<R>(arg0: $Function_<B, $Stream<R>, B, $Stream<R>>): $StreamView<R>;"),
                "stream flatMap keeps the concrete container in parameter position: " + streamView);
        assertTrue(streamView.contains("iterator(): $Iterator<B>;"),
                "covariant iterator return stays: " + streamView);
        assertTrue(streamView.contains("parallel(): $StreamView<B>"),
                "inherited BaseStream members are merged: " + streamView);
        assertFalse(streamView.contains("reduce"), "reduce is excluded by rule 2: " + streamView);
        assertFalse(streamView.contains("sorted"), "sorted is excluded by rule 2: " + streamView);
        assertFalse(streamView.contains("collect"), "collect is excluded by rule 2: " + streamView);
        assertFalse(streamView.contains("toList"), "toList is excluded by rule 3: " + streamView);
        assertFalse(streamView.contains("spliterator"), "spliterator is excluded by rule 3: " + streamView);
        assertTrue(streamFile.contains("$OptionalView") && streamFile.contains("java:java/util"),
                "java/util/stream imports the optional view it references");
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
