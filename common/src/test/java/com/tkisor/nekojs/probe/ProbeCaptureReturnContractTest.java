package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static org.junit.jupiter.api.Assertions.*;

class ProbeCaptureReturnContractTest {
    public static class Base {
        public final String label;
        public Base(String label) { this.label = label; }
    }
    public static class Child extends Base {
        public final int code = 7;
        public Child() { super("child"); }
    }
    public interface Parent<T> { Optional<? extends T> values(); }
    public interface Middle<U> extends Parent<U> {}
    public static class Implementation implements Middle<Base> {
        @Override public Optional<Child> values() { return Optional.of(new Child()); }
    }
    public static class RepeatedOverride extends Implementation {
        @Override public Optional<Child> values() { return super.values(); }
    }
    public static class WildcardOverride implements Middle<Base> {
        @Override public Optional<? extends Child> values() { return Optional.of(new Child()); }
    }
    public interface WildcardMiddle extends Parent<Base> {
        @Override Optional<? extends Child> values();
    }
    public static class WildcardMiddleImplementation implements WildcardMiddle {
        @Override public Optional<Child> values() { return Optional.of(new Child()); }
    }
    public interface ParameterParent<T> {
        Optional<? extends T> values(T input);
        Optional<? extends T> values(T[] input);
    }
    public static class ParameterImplementation implements ParameterParent<Base> {
        @Override public Optional<Child> values(Base input) { return Optional.of(new Child()); }
        @Override public Optional<Child> values(Base[] input) { return Optional.of(new Child()); }
        public Optional<String> values(int input) { return Optional.of(Integer.toString(input)); }
    }
    public static class MethodBase {
        public Optional<Child> values(Base input) { return Optional.of(new Child()); }
        public Optional<Child> values(Base[] input) { return Optional.of(new Child()); }
    }
    public static class InheritedImplementation extends MethodBase implements ParameterParent<Base> {}
    public static class Calls {
        public static Parent<Base> parent() { return new Implementation(); }
        public static Parent<Base> empty() { return Optional::empty; }
        public static ParameterParent<Base> parameterParent() { return new ParameterImplementation(); }
        public static Base[] inputs() { return new Base[]{new Base("input")}; }
        public static Optional<Base> base() { return Optional.of(new Base("base")); }
        public static Optional<Child> emptyChild() { return Optional.empty(); }
        public static Supplier<Optional<Child>> childSupplier() { return () -> Optional.of(new Child()); }
        public static BinaryOperator<Base> operator() { return (left, right) -> new Base(left.label + "+" + right.label); }
        public static Collector<Base, ?, List<String>> collector() {
            return java.util.stream.Collectors.mapping(value -> value.label, java.util.stream.Collectors.toList());
        }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void inheritedCaptureOperationsUseTheRealHostSurface() {
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> name.equals(Calls.class.getName()))
                .allowCreateProcess(false).option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022").build()) {
            assertEquals("base|child|child|base+child|child|1", context.eval("js", """
                    var Calls = Java.type('%s');
                    var empty = Calls.empty().values();
                    var full = Calls.parent().values();
                    [empty.or(() => Calls.base()).get().label,
                     empty.or(Calls.childSupplier()).get().label,
                     empty.or({get: () => full}).get().label,
                     full.stream().reduce(Calls.base().get(), Calls.operator()).label,
                     full.stream().reduce(Calls.operator()).get().label,
                     full.stream().toList().size()].join('|')
                    """.formatted(Calls.class.getName())).asString());
            assertEquals(1, context.eval("js", """
                    full.stream().collect(() => [], (items, value) => items.push(value.label),
                        (left, right) => right.forEach(value => left.push(value))).length
                    """).asInt());
            assertEquals("child", context.eval("js", "full.stream().collect(Calls.collector()).get(0)").asString());
            assertEquals("base", context.eval("js", "Calls.emptyChild().orElse(Calls.base().get()).label").asString());
            assertEquals("child", context.eval("js", "Calls.parameterParent().values(Calls.base().get()).get().label").asString());
            assertEquals("child", context.eval("js", "Calls.parameterParent().values(Calls.inputs()).get().label").asString());
            assertEquals("1", context.eval("js", "Calls.parameterParent().values(1).get()").asString());
        }
    }

    @Test
    void backendKeepsNarrowReturnAndInheritedCaptureContract(@TempDir Path temp) throws Exception {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of("java.time"), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(Base.class, Child.class, Parent.class,
                Middle.class, Implementation.class, RepeatedOverride.class, WildcardOverride.class,
                WildcardMiddle.class, WildcardMiddleImplementation.class,
                ParameterParent.class, ParameterImplementation.class, MethodBase.class, InheritedImplementation.class),
                config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"));
        var files = new TypeScriptProbeBackend().render(context);
        String declaration = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertTrue(declaration.contains("values(): $Optional<$ProbeCaptureReturnContractTest$Child> & "
                + "$OptionalView<$ProbeCaptureReturnContractTest$Base>;"), declaration);
        String repeated = declaration.substring(declaration.indexOf("export class $ProbeCaptureReturnContractTest$RepeatedOverride"));
        assertTrue(repeated.substring(0, repeated.indexOf("\n    }")).contains(
                "values(): $Optional<$ProbeCaptureReturnContractTest$Child> & "
                        + "$OptionalView<$ProbeCaptureReturnContractTest$Base>;"), declaration);
        assertTrue(declaration.contains("values(): $OptionalView<$ProbeCaptureReturnContractTest$Child> & "
                + "$OptionalView<$ProbeCaptureReturnContractTest$Base>;"), declaration);
        String inherited = declaration.substring(declaration.indexOf("export class $ProbeCaptureReturnContractTest$InheritedImplementation"));
        assertTrue(inherited.substring(0, inherited.indexOf("\n    }")).contains(
                " & $OptionalView<$ProbeCaptureReturnContractTest$Base>;"),
                "The receiving subclass supplies the generic interface context: " + declaration);
        assertEquals(4, java.util.regex.Pattern.compile("values\\([^\\n]+\\): "
                + "\\$Optional<\\$ProbeCaptureReturnContractTest\\$Child> & "
                + "\\$OptionalView<\\$ProbeCaptureReturnContractTest\\$Base>;").matcher(declaration).results().count(),
                "A specialized generic parameter still overrides the erased parent signature: " + declaration);
        assertFalse(declaration.contains("$Optional<string> &"),
                "An unrelated overload must not inherit the capture contract: " + declaration);
        Path output = Path.of("build/probe-ts-capture-return-contract");
        if (Files.exists(output)) {
            try (var entries = Files.walk(output)) {
                for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(entry);
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
        // The unrelated java.time hierarchy stays in the full ten-entrypoint diagnostic baseline.
        Files.writeString(output.resolve("java-time-ambient.d.ts"), """
                declare class $Duration { static toString(): string; }
                declare class $Instant { static toString(): string; }
                export { $Duration, $Instant };
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type {
                    $ProbeCaptureReturnContractTest$Base as Base,
                    $ProbeCaptureReturnContractTest$Child as Child,
                    $ProbeCaptureReturnContractTest$Parent as Parent,
                    $ProbeCaptureReturnContractTest$Implementation as Implementation,
                    $ProbeCaptureReturnContractTest$RepeatedOverride as RepeatedOverride,
                    $ProbeCaptureReturnContractTest$WildcardOverride as WildcardOverride,
                    $ProbeCaptureReturnContractTest$ParameterParent as ParameterParent,
                    $ProbeCaptureReturnContractTest$ParameterImplementation as ParameterImplementation,
                    $ProbeCaptureReturnContractTest$InheritedImplementation as InheritedImplementation,
                    $ProbeCaptureReturnContractTest$WildcardMiddle as WildcardMiddle,
                    $ProbeCaptureReturnContractTest$WildcardMiddleImplementation as WildcardMiddleImplementation
                } from 'java:com/tkisor/nekojs/probe';
                declare const implementation: Implementation;
                declare const input: Base;
                const direct: Child = implementation.values().get();
                const code: number = implementation.values().get().code;
                const inheritedFallback: Base = implementation.values().orElse(input);
                declare const repeated: RepeatedOverride;
                declare const wildcard: WildcardOverride;
                const repeatedFallback: Base = repeated.values().orElse(input);
                const repeatedCode: number = repeated.values().get().code;
                const wildcardFallback: Base = wildcard.values().orElse(input);
                const wildcardCode: number = wildcard.values().get().code;
                // @ts-expect-error A broader inherited fallback does not promise the narrow member.
                implementation.values().orElse(input).code;
                const parent: Parent<Base> = implementation;
                declare const layered: WildcardMiddleImplementation;
                const layeredMiddle: WildcardMiddle = layered;
                const layeredParent: Parent<Base> = layered;
                const layeredChild: Child = layered.values().get();
                const layeredFallback: Base = layeredParent.values().orElse(input);
                // @ts-expect-error An inherited upper-bound fallback does not retain the narrow payload.
                layered.values().orElse(input).code;
                const broad: Base = parent.values().get();
                declare const parameterImplementation: ParameterImplementation;
                const parameterParent: ParameterParent<Base> = parameterImplementation;
                const parameterNarrow: Child = parameterImplementation.values(input).get();
                const parameterBroad: Base = parameterParent.values(input).get();
                const parameterFallback: Base = parameterImplementation.values(input).orElse(input);
                declare const inherited: InheritedImplementation;
                const inheritedParameterFallback: Base = inherited.values(input).orElse(input);
                const inheritedParameterNarrow: Child = inherited.values(input).get();
                declare const hostInputs: Base[];
                const arrayNarrow: Child = parameterImplementation.values(hostInputs).get();
                const arrayBroad: Base = parameterParent.values(hostInputs).get();
                const overloaded: string = parameterImplementation.values(1).get();
                // @ts-expect-error A parent-typed result cannot promise the implementation's narrow members.
                parent.values().get().code;
                // @ts-expect-error The narrow return remains the direct-call contract.
                const wrong: string = implementation.values().get();
                """);

        var edited = new TypeReflector().reflect(Parent.class);
        edited.methods.stream().filter(method -> method.name.equals("values")).forEach(method -> method.hidden = true);
        edited.mutated = true;
        var hidden = new ProbeContext.Of(snapshot, context.collectedClasses(), config, context.paths(),
                "typescript", context.languageDir(), List.of(edited));
        String hiddenDeclaration = new TypeScriptProbeBackend().render(hidden)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(hiddenDeclaration.contains("values(): $Optional<$ProbeCaptureReturnContractTest$Child> &"),
                "A hidden parent contract must not be recovered from reflection: " + hiddenDeclaration);

        var renamed = new TypeReflector().reflect(Parent.class);
        renamed.methods.stream().filter(method -> method.name.equals("values"))
                .forEach(method -> method.renameTo = "renamedValues");
        renamed.mutated = true;
        var renamedContext = new ProbeContext.Of(snapshot, context.collectedClasses(), config, context.paths(),
                "typescript", context.languageDir(), List.of(renamed));
        String renamedDeclaration = new TypeScriptProbeBackend().render(renamedContext)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(renamedDeclaration.contains("values(): $Optional<$ProbeCaptureReturnContractTest$Child> &"),
                "A renamed parent method cannot create a facet for the old name: " + renamedDeclaration);

        var overridden = new TypeReflector().reflect(Parent.class);
        overridden.methods.stream().filter(method -> method.name.equals("values")).forEach(method -> {
            method.returnType.overridden = true;
            method.returnType.ref = ApiTypeRef.primitive("boolean");
        });
        overridden.mutated = true;
        var overriddenContext = new ProbeContext.Of(snapshot, context.collectedClasses(), config, context.paths(),
                "typescript", context.languageDir(), List.of(overridden));
        String overriddenDeclaration = new TypeScriptProbeBackend().render(overriddenContext)
                .get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertFalse(overriddenDeclaration.contains("values(): $Optional<$ProbeCaptureReturnContractTest$Child> &"),
                "An edited parent return owns its contract: " + overriddenDeclaration);
    }
}
