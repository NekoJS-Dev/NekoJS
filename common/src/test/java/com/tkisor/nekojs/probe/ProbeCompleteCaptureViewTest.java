package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import static org.junit.jupiter.api.Assertions.*;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Exercises the complete inherited capture surface through the public backend and strict consumers. */
class ProbeCompleteCaptureViewTest {
    public static class SameBound implements ProbeWildcardOptionalCaptureTest.ConstableLike {
        @Override public java.util.Optional<ProbeWildcardOptionalCaptureTest.ConstDesc> describeConstable() {
            return java.util.Optional.empty();
        }
    }
    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void generateCompleteCaptureConsumer(@TempDir Path temp) throws Exception {
        writeConsumer(context(temp, List.of()), Path.of("build/probe-ts-complete-capture"), """
                import { $Optional } from 'java:java/util';
                import type {
                    $ProbeWildcardOptionalCaptureTest$ConstableLike as Parent,
                    $ProbeWildcardOptionalCaptureTest$ConstDesc as Bound,
                    $ProbeWildcardOptionalCaptureTest$DynamicDesc as Narrow,
                    $ProbeWildcardOptionalCaptureTest$BooleanLike as Implementation
                } from 'java:com/tkisor/nekojs/probe';
                declare const implementation: Implementation;
                const parent: Parent = implementation;
                declare const sameBound: import('java:com/tkisor/nekojs/probe').$ProbeCompleteCaptureViewTest$SameBound;
                const sameParent: Parent = sameBound;
                const result = parent.describeConstable();
                const upper: Bound = result.get();
                const narrow: Narrow = implementation.describeConstable().get();
                // @ts-expect-error The upper bound does not expose narrow members.
                upper.code;
                // @ts-expect-error The value kind remains checked.
                const wrong: string = result.get();

                const fallback: Bound = result.or(() => $Optional.empty()).get();
                declare const genericHost: { get: <C extends Bound>() => $Optional<C> };
                const fromHost: Bound = result.or(genericHost).get();
                // @ts-expect-error An unrelated supplier payload remains rejected.
                result.or(() => $Optional.of('wrong'));

                const reduced: Bound = result.stream().reduce((left, right) => left).get();
                // @ts-expect-error An unrelated reducer result remains rejected.
                result.stream().reduce((left, right) => 'wrong');

                const list = result.stream().toList();
                const item: Bound = list.get(0);
                const nested: Bound = list.reversed().subList(0, 1).get(0);
                const iterated: Bound = list.listIterator().next();
                list.replaceAll(value => value);
                list.add(upper);
                list.addAll([]);
                // @ts-expect-error An unrelated element kind remains rejected.
                list.add('wrong');
                // @ts-expect-error A callback cannot produce an unrelated element kind.
                list.replaceAll(value => 'wrong');
                // @ts-expect-error Upper-bound reads do not gain narrow members.
                list.get(0).code;

                import { $UnaryOperator, $BinaryOperator } from 'java:java/util/function';
                list.replaceAll($UnaryOperator.identity());
                const hostReduced: Bound = result.stream().reduce($BinaryOperator.maxBy((left: Bound, right: Bound) => 0)).get();

                const identity = $UnaryOperator.identity<Bound>();
                // @ts-expect-error A generic identity does not fabricate a narrow result from a broad value.
                const invented: Narrow = identity.apply(upper);
                // @ts-expect-error The source apply method cannot be specialized into a narrow declaration.
                identity.apply<Narrow>(upper);
                // @ts-expect-error A different element kind cannot provide the captured operator.
                list.replaceAll($UnaryOperator.identity<string>());

                declare const wrongDomain: <C extends string>(value: C) => C;
                // @ts-expect-error A callback's quantified domain must admit the capture bound.
                list.replaceAll(wrongDomain);
                declare const wrongBinary: <C extends Bound, D extends Narrow>(left: C, right: D) => C;
                // @ts-expect-error Both operand domains must admit the capture bound.
                result.stream().reduce(wrongBinary);

                declare const boundUnary: import('java:java/util/function').$UnaryOperator<Bound>;
                declare const boundBinary: import('java:java/util/function').$BinaryOperator<Bound>;
                list.replaceAll(boundUnary);
                result.stream().reduce(boundBinary);
                const seeded: Bound = result.stream().reduce(upper, boundBinary);
                list.replaceAll($UnaryOperator.identity<Bound>());
                declare const narrowUnary: import('java:java/util/function').$UnaryOperator<Narrow>;
                declare const narrowBinary: import('java:java/util/function').$BinaryOperator<Narrow>;
                // @ts-expect-error A narrowed host cannot consume every possible bound element.
                list.replaceAll(narrowUnary);
                // @ts-expect-error Both host operand domains must accept the bound.
                result.stream().reduce(narrowBinary);
                // @ts-expect-error An explicit narrow factory cannot consume the whole bound.
                list.replaceAll($UnaryOperator.identity<Narrow>());
                // @ts-expect-error A narrow binary factory cannot consume the whole bound.
                result.stream().reduce($BinaryOperator.maxBy<Narrow>((left, right) => 0));
                // @ts-expect-error Seeded reductions keep both operator operand domains checked.
                result.stream().reduce(upper, narrowBinary);

                const retainedReduce: number = result.stream().reduce(
                    0, (count: number, value: Bound): number => count, (left: number, right: number): number => left);
                const retainedCollect: string[] = result.stream().collect(
                    (): string[] => [], (acc: string[], value: Bound): void => {}, (left: string[], right: string[]): void => {});
                const retainedDeep: Bound = result.stream().findAny().stream().findFirst().get();
                const retainedMap: string = result.map<string>((value: Bound) => value.label).get();
                const retainedFlatMap: Bound = result.flatMap<Bound>(() => $Optional.of(upper)).get();

                declare const ordinarySupplier: import('java:java/util/function').$Supplier<import('java:java/util').$Optional<Narrow>>;
                const ordinaryFallback: Bound = result.or(ordinarySupplier).get();
                const broadFallback: Bound = result.or(() => $Optional.of(upper)).get();
                declare const wrongSupplier: import('java:java/util/function').$Supplier<import('java:java/util').$Optional<string>>;
                // @ts-expect-error The supplier's actual payload remains bounded.
                result.or(wrongSupplier);
                // @ts-expect-error A proxy's payload remains bounded, without contextual any leakage.
                result.or({get: () => $Optional.of('wrong')});

                import type { $Collection, $List } from 'java:java/util';
                import type { $Collector } from 'java:java/util/stream';
                declare const narrowCollection: $Collection<Narrow>;
                list.addAll(narrowCollection);
                list.addAll(0, narrowCollection);
                list.replaceAll(value => upper);
                list.replaceAll({apply: (value: Bound) => value});
                // @ts-expect-error Proxy operators retain the actual operand domain.
                list.replaceAll({apply: (value: Narrow) => value});
                // @ts-expect-error Proxy operator results retain the bound.
                list.replaceAll({apply: (value: Bound) => 'wrong'});
                const replacement: Bound = result.stream().reduce((left, right) => upper).get();
                const replacementSeeded: Bound = result.stream().reduce(upper, (left, right) => upper);
                const proxyFallback: Bound = result.or({get: () => $Optional.of(narrow)}).get();
                declare const collector: $Collector<Bound, unknown, string[]>;
                const byCollector: string[] = result.stream().collect(collector);
                const explicitCollector: string[] = result.stream().collect<string[], unknown>(collector);
                declare const narrowCollector: $Collector<Narrow, unknown, Narrow[]>;
                // @ts-expect-error A collector cannot assume narrower stream operands.
                result.stream().collect(narrowCollector);
                declare const wrongCollector: $Collector<string, unknown, string[]>;
                // @ts-expect-error Unrelated collector operands remain rejected.
                result.stream().collect(wrongCollector);
                const rawOptional: $Optional<Bound> = result;
                const rawStream: $Stream<Bound> = result.stream();
                const rawList: $List<Bound> = list;
                const listValue: Bound = rawList.get(0);
                import { $Collectors, $Stream } from 'java:java/util/stream';
                const inferredList = result.stream().collect($Collectors.toList());
                const inferredItem: Bound = inferredList.get(0);
                const explicitList = result.stream().collect($Collectors.toList<Bound>());
                const explicitItem: Bound = explicitList.get(0);
                declare const broadCollector: $Collector<unknown, unknown, string[]>;
                const broadCollection: string[] = result.stream().collect(broadCollector);
                const broadFlatMap: Bound = result.flatMap<Bound>(() => $Optional.of(narrow)).get();
                const broadStreamFlatMap: Bound = result.stream().flatMap<Bound>(() => $Stream.of(narrow)).findFirst().get();
                const recursiveList: Bound = list.stream().toList().get(0);
                // @ts-expect-error Returning through List.stream must retain the operand domain.
                list.stream().reduce(narrowBinary);
                // @ts-expect-error Recursive navigation must retain the collector operand domain.
                list.reversed().subList(0, 1).stream().collect(narrowCollector);
                """);
    }
    @Test
    void generateEditedCaptureConsumer(@TempDir Path temp) throws Exception {
        TypeReflector reflector = new TypeReflector();
        TypeDecl optional = reflector.reflect(java.util.Optional.class);
        optional.mutated = true;
        for (MethodDecl method : optional.methods) {
            if (method.name.equals("filter")) { method.renameTo = "keep"; method.explicitlyEdited = true; }
            if (method.name.equals("orElseThrow")) method.hidden = true;
            if (method.name.equals("map") || method.name.equals("isPresent")) {
                method.returnType.overridden = true;
                method.returnType.ref = ApiTypeRef.primitive(method.name.equals("map") ? "boolean" : "string");
            }
            if (method.name.equals("or")) method.overloads.add(new MethodDecl.Overload(List.of("arg0: \"custom\""), "boolean", List.of()));
        }
        TypeDecl stream = reflector.reflect(java.util.stream.Stream.class);
        stream.mutated = true;
        stream.methods.stream().filter(method -> method.name.equals("toList")).forEach(method -> method.hidden = true);
        TypeDecl map = reflector.reflect(java.util.HashMap.class);
        map.mutated = true;
        map.renameTo = "OptionalView";
        TypeDecl predicate = reflector.reflect(java.util.function.Predicate.class);
        predicate.mutated = true;
        predicate.methods.stream().filter(method -> method.name.equals("test")).forEach(method -> {
            method.renameTo = "testRenamed";
            method.explicitlyEdited = true;
        });
        TypeDecl list = reflector.reflect(java.util.ArrayList.class);
        list.mutated = true;
        list.renameTo = "NekoCaptureOptionalInput";
        writeConsumer(context(temp, List.of(optional, stream, map, list, predicate)), Path.of("build/probe-ts-complete-capture-edited"), """
                import type { $ProbeWildcardOptionalCaptureTest$ConstableLike as Parent } from 'java:com/tkisor/nekojs/probe';
                declare const parent: Parent;
                const result = parent.describeConstable();
                const kept: string = result.keep(value => true).get().label;
                const mapped: boolean = result.map<string>(value => value.label);
                const present: string = result.present;
                const custom: boolean = result.or('custom');
                import type { $ProbeWildcardOptionalCaptureTest$DynamicDesc as Narrow } from 'java:com/tkisor/nekojs/probe';
                import type { $Predicate, $BinaryOperator } from 'java:java/util/function';
                declare const broadPredicate: $Predicate<import('java:com/tkisor/nekojs/probe').$ProbeWildcardOptionalCaptureTest$ConstDesc>;
                result.keep(broadPredicate);
                declare const narrowPredicate: $Predicate<Narrow>;
                declare const narrowOperator: $BinaryOperator<Narrow>;
                result.keep({test: value => true});
                // @ts-expect-error A renamed SAM host still needs the complete operand domain.
                result.keep(narrowPredicate);
                // @ts-expect-error Renamed navigation must not fall back to an unguarded native stream.
                result.keep(value => true).stream().reduce(narrowOperator);
                const inherited: string = result.stream().findFirst().get().label;
                // @ts-expect-error Renamed methods must not be restored from reflection.
                result.filter(() => true);
                // @ts-expect-error Hidden overloads must not be restored from reflection.
                result.orElseThrow();
                // @ts-expect-error The edited return is boolean, not a generated optional view.
                result.map<string>(value => value.label).get();
                // @ts-expect-error The edited Bean property keeps its authored read type.
                const wrongPresent: boolean = result.present;
                // @ts-expect-error Hidden Stream methods must not be regenerated.
                result.stream().toList();
                """);
    }

    @Test
    void generateAuthoredCaptureConsumers(@TempDir Path temp) throws Exception {
        var optional = ClassDeclarationCatalogEntry.of(java.util.Optional.class,
                "export class $Optional<T> { authoredOnly(): string; }");
        writeConsumer(context(temp, List.of(), List.of(optional), List.of("java.time")),
                Path.of("build/probe-ts-complete-capture-authored-optional"), """
                import type { $ProbeWildcardOptionalCaptureTest$ConstableLike as Parent } from 'java:com/tkisor/nekojs/probe';
                declare const parent: Parent;
                const authored: string = parent.describeConstable().authoredOnly();
                // @ts-expect-error Authored replacements must not regain native members from reflection.
                parent.describeConstable().get();
                // @ts-expect-error Native Bean aliases must not be restored either.
                parent.describeConstable().present;
                """);
        var stream = ClassDeclarationCatalogEntry.of(java.util.stream.Stream.class,
                "export interface $Stream<T> { authoredStream(): string; }");
        writeConsumer(context(temp, List.of(), List.of(stream), List.of("java.time")),
                Path.of("build/probe-ts-complete-capture-authored-stream"), """
                import type { $ProbeWildcardOptionalCaptureTest$ConstableLike as Parent } from 'java:com/tkisor/nekojs/probe';
                declare const parent: Parent;
                const authored: string = parent.describeConstable().stream().authoredStream();
                // @ts-expect-error Authored Stream replacements retain their own public surface.
                parent.describeConstable().stream().count();
                """);
    }

    @Test
    void unavailableCaptureSourcesRemainUnavailable(@TempDir Path temp) throws Exception {
        for (Class<?> source : List.of(java.util.Optional.class, java.util.stream.Stream.class)) {
            TypeDecl hidden = new TypeReflector().reflect(source);
            hidden.hidden = true;
            var files = new TypeScriptProbeBackend().render(context(temp, List.of(hidden)));
            String helper = source == java.util.Optional.class ? "$OptionalView" : "$StreamView";
            assertTrue(files.values().stream().noneMatch(text -> text.contains("export interface " + helper)),
                    "A hidden capture source must not be reflected again: " + source.getName());
            var report = com.google.gson.JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
            assertFalse(report.get("complete").getAsBoolean());
            assertEquals("hidden", report.getAsJsonObject("unavailable").get(source.getName()).getAsString());
        }
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(), List.of(),
                List.of("java.time", "java.util.stream")));
        assertTrue(files.values().stream().noneMatch(text -> text.contains("export interface $StreamView")));
        var report = com.google.gson.JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("excluded", report.getAsJsonObject("unavailable").get("java.util.stream.Stream").getAsString());
    }

    @Test
    void generateReusedBackendConsumer(@TempDir Path temp) throws Exception {
        TypeDecl optional = new TypeReflector().reflect(java.util.Optional.class);
        optional.mutated = true;
        optional.methods.stream().filter(method -> method.name.equals("isPresent")).forEach(method -> {
            method.returnType.overridden = true;
            method.returnType.ref = ApiTypeRef.primitive("string");
        });
        TypeDecl renamed = new TypeReflector().reflect(java.util.HashMap.class);
        renamed.mutated = true;
        renamed.renameTo = "OptionalView";
        TypeScriptProbeBackend backend = new TypeScriptProbeBackend();
        backend.render(context(temp, List.of(optional, renamed)));
        writeConsumer(backend, context(temp, List.of()), Path.of("build/probe-ts-complete-capture-reused"), """
                import type { $ProbeWildcardOptionalCaptureTest$ConstableLike as Parent,
                    $ProbeWildcardOptionalCaptureTest$ConstDesc as Bound } from 'java:com/tkisor/nekojs/probe';
                import type { $OptionalView } from 'java:java/util';
                declare const parent: Parent;
                const result: $OptionalView<Bound> = parent.describeConstable();
                const present: boolean = result.present;
                const label: string = result.filter(value => true).get().label;
                // @ts-expect-error The previous snapshot's getter edit must not leak.
                const stalePresent: string = result.present;
                """);
    }

    private static ProbeContext.Of context(Path temp, List<TypeDecl> edited) {
        return context(temp, edited, List.of(), List.of("java.time"));
    }

    private static ProbeContext.Of context(Path temp, List<TypeDecl> edited,
                                           List<ClassDeclarationCatalogEntry> authored, List<String> excluded) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), authored, List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(ProbeCompleteCaptureViewTest.class.getPackageName()), List.of(), excluded, List.of(), 1, "SMART"));
        List<Class<?>> classes = new java.util.ArrayList<>(List.of(ProbeWildcardOptionalCaptureTest.ConstableLike.class,
                ProbeWildcardOptionalCaptureTest.BooleanLike.class, SameBound.class, java.util.stream.Collectors.class));
        edited.forEach(type -> classes.add(type.sourceClass));
        return new ProbeContext.Of(snapshot, classes.stream().distinct().toList(), config,
                NekoJSPaths.fromGameDir(temp), "typescript", temp.resolve("typescript"), edited);
    }

    private static void writeConsumer(ProbeContext.Of context, Path output, String exercise) throws Exception {
        writeConsumer(new TypeScriptProbeBackend(), context, output, exercise);
    }

    private static void writeConsumer(TypeScriptProbeBackend backend, ProbeContext.Of context,
                                       Path output, String exercise) throws Exception {
        var files = backend.render(context);
        if (Files.exists(output)) {
            try (var entries = Files.walk(output)) {
                for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(entry);
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
        // The unrelated chrono diagnostics remain in the full ten-entrypoint baseline.
        Files.writeString(output.resolve("java-time-ambient.d.ts"), """
                declare class $Duration { static toString(): string; }
                declare class $Instant { static toString(): string; }
                export { $Duration, $Instant };
                """);
        Files.writeString(output.resolve("exercise.ts"), exercise);
    }

}
