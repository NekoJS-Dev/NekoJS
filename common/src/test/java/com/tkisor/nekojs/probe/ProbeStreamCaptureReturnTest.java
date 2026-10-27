package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
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
import java.util.stream.Stream;
import graal.graalvm.polyglot.Context;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises narrow Stream implementations through their wildcard parent contract. */
class ProbeStreamCaptureReturnTest {
    public interface Parent<T> {
        Stream<? extends T> getSelfAndPassengers();
        Stream<? extends T> getPassengersAndSelf();
    }

    public interface NarrowParent extends Parent<ProbeCaptureReturnContractTest.Base> {
        @Override Stream<? extends ProbeCaptureReturnContractTest.Child> getSelfAndPassengers();
        @Override Stream<? extends ProbeCaptureReturnContractTest.Child> getPassengersAndSelf();
    }

    public interface OptionalParent<T> {
        java.util.Optional<? extends T> getSelfAndPassengers();
        java.util.Optional<? extends T> getPassengersAndSelf();
    }

    public static class Implementation implements NarrowParent {
        @Override public Stream<ProbeCaptureReturnContractTest.Child> getSelfAndPassengers() {
            return Stream.of(new ProbeCaptureReturnContractTest.Child(), new ProbeCaptureReturnContractTest.Child());
        }
        @Override public Stream<ProbeCaptureReturnContractTest.Child> getPassengersAndSelf() {
            return getSelfAndPassengers();
        }
    }

    public static class Calls {
        public static Parent<ProbeCaptureReturnContractTest.Base> parent() { return new Implementation(); }
        public static java.util.function.BinaryOperator<ProbeCaptureReturnContractTest.Base> operator() {
            return ProbeCaptureReturnContractTest.Calls.operator();
        }
    }

    @Test
    void covariantStreamsUseTheRealHostSurface() {
        TestPlatformInit.ensureInitialized();
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> name.equals(Calls.class.getName()))
                .allowCreateProcess(false).option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022").build()) {
            assertEquals("child+child|child", context.eval("js", """
                    var Calls = Java.type('%s');
                    var parent = Calls.parent();
                    var reduced = parent.getSelfAndPassengers().reduce(Calls.operator()).get().label;
                    var first = parent.getPassengersAndSelf().map(value => value.label).toList().get(0);
                    reduced + '|' + first
                    """.formatted(Calls.class.getName())).asString());
        }
    }

    @Test
    void generateCovariantStreamConsumer(@TempDir Path temp) throws Exception {
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(), List.of()));
        writeConsumer(Path.of("build/probe-ts-stream-capture-return"), files, """
                import type {
                  $ProbeStreamCaptureReturnTest$Parent as Parent,
                  $ProbeStreamCaptureReturnTest$NarrowParent as NarrowParent,
                  $ProbeStreamCaptureReturnTest$Implementation as Implementation,
                  $ProbeCaptureReturnContractTest$Base as Base,
                  $ProbeCaptureReturnContractTest$Child as Child
                } from 'java:com/tkisor/nekojs/probe';
                import type { $BinaryOperator } from 'java:java/util/function';
                declare const implementation: Implementation;
                const parent: Parent<Base> = implementation;
                const middle: NarrowParent = implementation;
                const middleCode: number = middle.getSelfAndPassengers().findFirst().get().code;
                const narrow: Child = implementation.getSelfAndPassengers().findFirst().get();
                const narrowBean: number = implementation.selfAndPassengers.findFirst().get().code;
                const broad: Base = parent.getPassengersAndSelf().findFirst().get();
                const name: string = parent.getSelfAndPassengers().map(value => value.label).toList().get(0);
                declare const operator: $BinaryOperator<Base>;
                const reduced: Base = implementation.getSelfAndPassengers().reduce(operator).get();
                declare const narrowOperator: $BinaryOperator<Child>;
                // @ts-expect-error A parent capture must admit the complete upper-bound operand domain.
                parent.getSelfAndPassengers().reduce(narrowOperator);
                // @ts-expect-error An upper-bound read does not acquire the narrow member.
                broad.code;
                // @ts-expect-error A captured reducer cannot return an unrelated value kind.
                parent.getPassengersAndSelf().reduce((left, right) => 'wrong');
                // @ts-expect-error The direct implementation still returns Child.
                const wrong: string = implementation.getSelfAndPassengers().findFirst().get();
                """);
    }

    @Test
    void generateAuthoredStreamConsumer(@TempDir Path temp) throws Exception {
        var authored = ClassDeclarationCatalogEntry.of(Stream.class, "export interface $Stream<T> { authored(): string; }");
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(), List.of(authored)));
        assertFalse(files.values().stream().anyMatch(text -> text.contains("export interface $StreamView")));
        writeConsumer(Path.of("build/probe-ts-stream-capture-authored"), files, """
                import type {
                  $ProbeStreamCaptureReturnTest$Parent as Parent,
                  $ProbeStreamCaptureReturnTest$Implementation as Implementation,
                  $ProbeCaptureReturnContractTest$Base as Base
                } from 'java:com/tkisor/nekojs/probe';
                declare const implementation: Implementation;
                const parent: Parent<Base> = implementation;
                const marker: string = parent.getSelfAndPassengers().authored();
                // @ts-expect-error A replacement must not regain native Stream methods.
                parent.getSelfAndPassengers().findFirst();
                """);
    }

    @Test
    void hiddenStreamDoesNotRegainCaptureDeclarations(@TempDir Path temp) {
        TypeDecl hidden = new TypeReflector().reflect(Stream.class);
        hidden.hidden = true;
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(hidden), List.of()));
        assertFalse(files.values().stream().anyMatch(text -> text.contains("export interface $StreamView")));
        var report = com.google.gson.JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("hidden", report.getAsJsonObject("unavailable").get(Stream.class.getName()).getAsString());
    }

    @Test
    void editedParentSlotsRetainAuthority(@TempDir Path temp) {
        for (String edit : List.of("hide", "rename", "return")) {
            TypeDecl parent = new TypeReflector().reflect(Parent.class);
            parent.mutated = true;
            for (var method : parent.methods) {
                if (!method.name.equals("getSelfAndPassengers")) continue;
                if (edit.equals("hide")) method.hidden = true;
                if (edit.equals("rename")) { method.renameTo = "renamed"; method.explicitlyEdited = true; }
                if (edit.equals("return")) { method.returnType.overridden = true; method.returnType.ref = ApiTypeRef.primitive("string"); }
            }
            var files = new TypeScriptProbeBackend().render(context(temp, List.of(parent), List.of()));
            String declaration = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
            String body = declaration.substring(declaration.indexOf("export class $ProbeStreamCaptureReturnTest$Implementation"));
            body = body.substring(0, body.indexOf("\n    }"));
            assertTrue(body.contains("getSelfAndPassengers(): $Stream<$ProbeCaptureReturnContractTest$Child>;"), edit + body);
            assertTrue(body.contains("getPassengersAndSelf(): $Stream<$ProbeCaptureReturnContractTest$Child> & $StreamView<$ProbeCaptureReturnContractTest$Base>;"), edit + body);
        }
    }

    @Test
    void editedHierarchyDoesNotMixCaptureContainers(@TempDir Path temp) {
        TypeDecl middle = new TypeReflector().reflect(NarrowParent.class);
        middle.mutated = true;
        // Preserve an edited hierarchy's incompatibility instead of inventing a different container's facet.
        var parent = middle.interfaces.getFirst();
        parent.ref = new ApiTypeRef(ApiTypeRef.Kind.SYMBOL, "java:" + OptionalParent.class.getName(), parent.ref.arguments(), null);
        parent.overridden = true;
        var foreign = new TypeReflector().reflect(OptionalParent.class);
        var files = new TypeScriptProbeBackend().render(context(temp, List.of(middle, foreign), List.of()));
        String declaration = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        String body = declaration.substring(declaration.indexOf("export class $ProbeStreamCaptureReturnTest$Implementation"));
        body = body.substring(0, body.indexOf("\n    }"));
        assertTrue(body.contains("getSelfAndPassengers(): $Stream<$ProbeCaptureReturnContractTest$Child>;"),
                "An Optional parent bound must not become a Stream facet: " + body);
    }

    private static ProbeContext.Of context(Path temp, List<TypeDecl> edited, List<ClassDeclarationCatalogEntry> authored) {
        TestPlatformInit.ensureInitialized();
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), authored, List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(ProbeStreamCaptureReturnTest.class.getPackageName()), List.of(), List.of("java.time"), List.of(), 1, "SMART"));
        List<Class<?>> classes = new java.util.ArrayList<>(List.of(Parent.class, NarrowParent.class, Implementation.class,
                ProbeCaptureReturnContractTest.Base.class, ProbeCaptureReturnContractTest.Child.class));
        edited.forEach(type -> classes.add(type.sourceClass));
        return new ProbeContext.Of(snapshot, classes.stream().distinct().toList(),
                config, NekoJSPaths.fromGameDir(temp), "typescript", temp.resolve("typescript"), edited);
    }

    private static void writeConsumer(Path output, Map<String, String> files, String exercise) throws Exception {
        Path build = Path.of("build").toAbsolutePath().normalize();
        Path target = output.toAbsolutePath().normalize();
        assertTrue(target.startsWith(build) && !target.equals(build), "Fixture output must remain inside build: " + target);
        if (Files.exists(target)) {
            Path resolved = target.toRealPath();
            assertTrue(resolved.startsWith(build.toRealPath()) && !resolved.equals(build.toRealPath()),
                    "Fixture output must not resolve outside build: " + resolved);
            try (var entries = Files.walk(target)) {
                for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(entry);
            }
        }
        for (var entry : files.entrySet()) {
            Path path = output.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"],"java:java/time": ["java-time-ambient.d.ts"]}},"include":["@package/**/*.d.ts","java-time-ambient.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("java-time-ambient.d.ts"), """
                declare class $Duration { static toString(): string; }
                declare class $Instant { static toString(): string; }
                export { $Duration, $Instant };
                """);
        Files.writeString(output.resolve("exercise.ts"), exercise);
    }
}
