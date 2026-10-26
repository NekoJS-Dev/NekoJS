package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeConstableValueProjectionTest {
    public static class VarargsConstructor {
        public final int count;
        @SafeVarargs
        public <T extends ConstantDesc> VarargsConstructor(T... values) { count = values.length; }
    }

    public static class Calls {
        public static Optional<? extends ConstantDesc> doubleDescription() { return Double.valueOf(1.5).describeConstable(); }
        public static Optional<? extends ConstantDesc> integerDescription() { return Integer.valueOf(7).describeConstable(); }
        public static Optional<? extends ConstantDesc> stringDescription() { return "value".describeConstable(); }
        public static ConstantDesc directDescription() { return Double.valueOf(1.5); }
        public static ConstantDesc classDescription() { return java.lang.constant.ClassDesc.of("java.lang.String"); }
        public static ConstantDesc[] descriptions() { return new ConstantDesc[]{1.5, "value", classDescription()}; }
        public static int acceptAll(ConstantDesc[] values) { return values.length; }
        public static int acceptVarargs(ConstantDesc... values) { return values.length; }
        public static ConstantDesc[] explicitArray() { return descriptions(); }
        public static int explicitVarargs(ConstantDesc... values) { return values.length; }
        public static int acceptDescriptions(List<? extends ConstantDesc> values) { return values.size(); }
        public static ConstantDesc acceptSupplier(java.util.function.Supplier<? extends ConstantDesc> value) { return value.get(); }
        public static ConstantDesc acceptFactory(java.util.function.Supplier<java.util.function.Supplier<ConstantDesc>> value) { return value.get().get(); }
        public static void consume(java.util.function.Consumer<ConstantDesc> consumer) { consumer.accept(1.5); }
        public static String acceptDescription(ConstantDesc value) { return value.getClass().getSimpleName(); }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void constantDescriptionsExposeProjectedScalarValues() {
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> name.equals(Calls.class.getName()) || name.equals(VarargsConstructor.class.getName()))
                .allowCreateProcess(false).option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022").build()) {
            String result = context.eval("js", """
                    var Calls = Java.type('%s');
                    [typeof Calls.doubleDescription().get(), typeof Calls.integerDescription().get(),
                     typeof Calls.stringDescription().get(), typeof Calls.directDescription(),
                     typeof Calls.doubleDescription().get().resolveConstantDesc,
                     typeof Calls.classDescription(), typeof Calls.classDescription().resolveConstantDesc].join('|')
                    """.formatted(Calls.class.getName())).asString();
            assertEquals("number|number|string|number|undefined|object|function", result);
            assertEquals("Double|Double|String|rejected", context.eval("js", """
                    [1.5, 7, 'value', true].map(value => {
                        try { return Calls.acceptDescription(value); }
                        catch (failure) { return 'rejected'; }
                    }).join('|')
                    """).asString());
            assertEquals(3, context.eval("js", "Calls.acceptAll([1.5, 'value', Calls.classDescription()])").asInt());
            assertEquals(3, context.eval("js", "Calls.acceptVarargs([1.5, 'value', Calls.classDescription()])").asInt());
            assertEquals(3, context.eval("js", "new (Java.type('" + VarargsConstructor.class.getName()
                    + "'))([1.5, 'value', Calls.classDescription()]).count").asInt());
        }
    }

    @Test
    void generatedConsumerRetainsScalarValuesAndHostDescriptions(@TempDir Path temp) throws Exception {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot,
                List.of(Calls.class, VarargsConstructor.class, Constable.class, ConstantDesc.class, Double.class, Float.class,
                        Integer.class, Long.class, String.class), config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"));
        Path output = Path.of("build/probe-ts-constable-values");
        writeConsumer(context, output, """
                import { $ProbeConstableValueProjectionTest$Calls as Calls } from 'java:com/tkisor/nekojs/probe';
                import { $ProbeConstableValueProjectionTest$VarargsConstructor as Constructor } from 'java:com/tkisor/nekojs/probe';
                import type { $ConstantDesc } from 'java:java/lang/constant';
                const direct: number | string | $ConstantDesc = Calls.directDescription();
                const captured: number | string | $ConstantDesc = Calls.doubleDescription().get();
                Calls.acceptDescription(1.5);
                Calls.acceptDescription('value');
                declare const host: $ConstantDesc;
                Calls.acceptDescription(host);
                const array: (number | string | $ConstantDesc)[] = Calls.descriptions();
                Calls.acceptAll([1.5, 'value', host]);
                Calls.acceptVarargs();
                Calls.acceptVarargs([1.5, 'value', host]);
                new Constructor([1.5, 'value', host]);
                // @ts-expect-error Constructor-local bounds keep the same scalar/host input domain.
                new Constructor([true]);
                // @ts-expect-error The varargs array keeps the actual element domain.
                Calls.acceptVarargs([true]);
                // @ts-expect-error Array elements keep the same actual value domain.
                Calls.acceptAll([true]);
                // @ts-expect-error Booleans are not constant descriptions.
                Calls.acceptDescription(true);
                // @ts-expect-error A projected scalar does not expose Java ConstantDesc methods.
                Calls.doubleDescription().get().resolveConstantDesc;
                // @ts-expect-error The result can also be a genuine non-scalar description.
                const scalarOnly: number = Calls.directDescription();
                """);

        var edited = new TypeReflector().reflect(Calls.class);
        edited.mutated = true;
        edited.methods.stream().filter(method -> method.name.equals("directDescription")).forEach(method -> {
            method.returnType.ref = ApiTypeRef.primitive("boolean");
            method.returnType.overridden = true;
        });
        edited.methods.stream().filter(method -> method.name.equals("acceptDescription")).forEach(method -> {
            method.params.getFirst().type.ref = ApiTypeRef.primitive("boolean");
            method.params.getFirst().type.overridden = true;
        });
        edited.methods.stream().filter(method -> method.name.equals("classDescription") || method.name.equals("descriptions"))
                .forEach(method -> method.returnType.overridden = true);
        edited.methods.stream().filter(method -> method.name.equals("acceptAll") || method.name.equals("acceptFactory"))
                .forEach(method -> method.params.getFirst().type.overridden = true);
        var explicitElements = ApiTypeRef.union(List.of(ApiTypeRef.primitive("string"), ApiTypeRef.primitive("number")));
        edited.methods.stream().filter(method -> method.name.equals("explicitArray")).forEach(method -> {
            method.returnType.ref = ApiTypeRef.array(explicitElements);
            method.returnType.overridden = true;
        });
        edited.methods.stream().filter(method -> method.name.equals("explicitVarargs")).forEach(method -> {
            method.params.getFirst().type.ref = explicitElements;
            method.params.getFirst().type.overridden = true;
        });
        var editedContext = new ProbeContext.Of(snapshot, context.collectedClasses(), config, context.paths(),
                "typescript", context.languageDir(), List.of(edited));
        writeConsumer(editedContext, output.resolve("edited"), """
                import { $ProbeConstableValueProjectionTest$Calls as Calls } from 'java:com/tkisor/nekojs/probe';
                import type { $ConstantDesc } from 'java:java/lang/constant';
                const explicit: boolean = Calls.directDescription();
                Calls.acceptDescription(true);
                const rawHost: $ConstantDesc = Calls.classDescription();
                rawHost.resolveConstantDesc;
                const rawArray: $ConstantDesc[] = Calls.descriptions();
                rawArray[0].resolveConstantDesc;
                const explicitArray: (string | number)[] = Calls.explicitArray();
                Calls.explicitVarargs(['value', 1.5]);
                // @ts-expect-error Explicit array element unions do not include boolean.
                explicitArray.push(true);
                Calls.acceptAll([rawHost]);
                Calls.acceptFactory(() => () => rawHost);
                // @ts-expect-error An explicitly retained host array does not gain primitive inputs.
                Calls.acceptAll([1.5]);
                // @ts-expect-error Explicit nested SAM slots retain their raw result references.
                Calls.acceptFactory(() => () => 1.5);
                // @ts-expect-error A raw override of another slot must not leak into ordinary outputs.
                const leakedHost: $ConstantDesc = Calls.doubleDescription().get();
                // @ts-expect-error Explicit slot edits retain authority over the source's scalar projection.
                Calls.acceptDescription(1.5);
                // @ts-expect-error The edited result is not the original constant description domain.
                const original: number = Calls.directDescription();
                """);

        var adaptedSnapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(),
                List.of(new AdapterCatalogEntry(ConstantDesc.class, List.of(AdapterInputShape.bool()),
                        ConversionPrecedence.LOWEST, Optional.empty())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var adaptedContext = new ProbeContext.Of(adaptedSnapshot, context.collectedClasses(), config,
                context.paths(), "typescript", context.languageDir());
        writeConsumer(adaptedContext, output.resolve("adapted"), """
                import { $ProbeConstableValueProjectionTest$Calls as Calls } from 'java:com/tkisor/nekojs/probe';
                import type { $ConstantDesc } from 'java:java/lang/constant';
                Calls.acceptDescription(true);
                Calls.acceptDescriptions([true]);
                Calls.acceptSupplier(() => true);
                Calls.acceptFactory(() => () => true);
                // @ts-expect-error Explicit adapter inputs have priority over intrinsic value alternatives.
                Calls.acceptDescription(1.5);
                // @ts-expect-error The wildcard input element keeps the explicit adapter domain.
                Calls.acceptDescriptions([1.5]);
                // @ts-expect-error Callback results are Java inputs and use the explicit adapter domain.
                Calls.acceptSupplier(() => 1.5);
                // @ts-expect-error Nested callback results retain the same conversion priority.
                Calls.acceptFactory(() => () => 1.5);
                Calls.consume(value => {
                    const received: number | string | $ConstantDesc = value;
                    // @ts-expect-error Java callback operands are outputs, not the adapter's input shape.
                    const inputOnly: boolean = value;
                });
                """);
    }

    private static void writeConsumer(ProbeContext context, Path output, String exercise) throws Exception {
        for (var entry : new TypeScriptProbeBackend().render(context).entrySet()) {
            Path destination = output.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), exercise);
    }
}
