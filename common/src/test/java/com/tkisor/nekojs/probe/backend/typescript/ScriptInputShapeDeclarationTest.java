package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCallSignature;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.testfixture.input.ScriptInputFixtures.*;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptInputShapeDeclarationTest {
    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void emptyMetadataKeepsTheOrdinaryEventDeclarationExactly() {
        var aliases = new TypeAliasRegistry();
        var generator = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases));
        var event = EventCatalogEntry.of("OrdinaryEvents", "visit", ScriptType.SERVER,
                othermod.events.EventTypes.Payload.class, null, false, false);
        var legacy = new EventCatalogEntry(event.group(), event.name(), event.scriptType(), event.eventType(),
                event.dispatchKeyType(), event.cancellable(), event.dispatchable(), event.snippet());
        String expected = """
                import { $EventTypes$Payload } from "java:othermod/events";

                declare module "@side-only/server/events" {
                }

                export {};

                declare global {
                    namespace OrdinaryEvents {
                        function visit(handler: ((event: $EventTypes$Payload) => void)): void;
                    }

                }
                """;
        assertEquals(expected, generator.generate(List.of(event), ScriptType.SERVER));
        assertEquals(expected, generator.generate(List.of(legacy.withCallSignatures(List.of())), ScriptType.SERVER));
    }

    @Test
    void callableShapesReplaceOnlyTheirHandlerAndCompleteHostDependencies() throws Exception {
        var parameters = new ArrayList<EventCallSignature.Parameter>();
        parameters.add(new EventCallSignature.Parameter("name", string()));
        parameters.add(new EventCallSignature.Parameter("options", new OptionsShape().inputShape()));
        var signature = new EventCallSignature(parameters, List.of(
                new EventCallSignature.Parameter("key", host(othermod.events.EventTypes.Key.class)),
                new EventCallSignature.Parameter("payload", host(othermod.events.EventTypes.Payload.class))), raw("void"));
        parameters.clear();
        assertEquals(2, signature.inputs().size());
        var signatures = new ArrayList<>(List.of(signature,
                new EventCallSignature(List.of(), List.of(), host(othermod.events.EventTypes.Key.class))));
        var callable = EventCatalogEntry.of("TypedEvents", "paint", ScriptType.SERVER,
                UnusedPayload.class, null, false, false).withCallSignatures(signatures);
        signatures.clear();
        assertEquals(2, callable.callSignatures().size());
        var ordinary = EventCatalogEntry.of("TypedEvents", "ordinary", ScriptType.SERVER,
                othermod.events.EventTypes.Payload.class, null, false, false);
        Path output = Path.of("build/probe-ts-script-input-shapes/callable");
        Map<String, String> files = new TypeScriptProbeBackend().render(context(output, List.of(callable, ordinary), List.of()));
        String events = files.get("@side-only/server/events/index.d.ts");
        assertTrue(events.contains("function paint(\n"
                + "            name: string,\n"
                + "            options: { format: (\"fast\" | \"precise\"), labels?: Record<string, string>, target?: $EventTypes$Key_, count?: number },\n"
                + "            callback: (key: $EventTypes$Key, payload: $EventTypes$Payload) => void\n"
                + "        ): void;"), events);
        assertTrue(events.contains("function paint(callback: () => void): $EventTypes$Key;"), events);
        assertTrue(events.contains("function ordinary(handler: ((event: $EventTypes$Payload) => void)): void;"), events);
        assertFalse(events.contains("paint(handler:"), events);
        assertFalse(events.contains("UnusedPayload"), events);
        assertTrue(events.contains("import { $EventTypes$Key, $EventTypes$Key_, $EventTypes$Payload } from \"java:othermod/events\";"), events);
        assertTrue(files.get("@package/othermod/events/index.d.ts").contains("count: number"));
        assertFalse(files.containsKey("@package/com/tkisor/nekojs/probe/testfixture/input/index.d.ts"));
        writeFixture(files, output, """
                TypedEvents.paint('demo', { format: 'fast', labels: { source: 'test' }, target: 'key' }, (key, payload) => {
                    const name: string = key.name;
                    const count: number = payload.count;
                    // @ts-expect-error Callback hosts must not widen to the input adapter's literal branch.
                    const literal: 'key' = key;
                    // @ts-expect-error Completed hosts retain their real member types.
                    const wrong: string = payload.count;
                });
                const key = TypedEvents.paint(() => {});
                const returnedName: string = key.name;
                // @ts-expect-error Explicit callable metadata removes the ordinary event subscription overload.
                TypedEvents.paint(event => { event.shouldNotAppear; });
                // @ts-expect-error Unknown options cannot escape through a reflected Map overload.
                TypedEvents.paint('demo', { format: 'fast', unknown: true }, (key, payload) => {});
                // @ts-expect-error Literal fields preserve their declared domain.
                TypedEvents.paint('demo', { format: 'other' }, (key, payload) => {});
                TypedEvents.ordinary(event => { const count: number = event.count; });
                """);
    }

    @Test
    void annotatedMapUsesOnlyTheProviderInputWhileRetainingJavaAndOutputTypes() throws Exception {
        var declaration = new TypeReflector().reflect(PostBuilder.class);
        var configure = declaration.methods.stream().filter(method -> method.name.equals("configure")).findFirst().orElseThrow();
        assertEquals(Map.class, ((java.lang.reflect.ParameterizedType) configure.params.getFirst().type.sourceType).getRawType());
        assertNotNull(configure.params.getFirst().type.inputShape);
        assertNotNull(declaration.constructors.getFirst().params.getFirst().type.inputShape);
        var getter = declaration.methods.stream().filter(method -> method.name.equals("getOptions")).findFirst().orElseThrow();
        assertNotNull(getter.setterParamType.inputShape);
        assertNull(getter.returnType.inputShape);
        String rendered = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertEquals(1, rendered.lines().filter(line -> line.contains("configure(")).count(), rendered);
        int configureStart = rendered.indexOf("configure(");
        String configureSignature = rendered.substring(configureStart, rendered.indexOf(';', configureStart) + 1);
        assertTrue(configureSignature.contains("{ format: (\"fast\" | \"precise\")"), rendered);
        assertFalse(configureSignature.contains("Map"), configureSignature);
        var inherited = new TypeReflector().reflect(InheritedPostBuilder.class);
        assertEquals(PostBuilder.class, inherited.superType.sourceType);
        assertTrue(inherited.methods.stream().noneMatch(method -> method.name.equals("configure")),
                "Ordinary superclass methods remain inherited instead of duplicated in the local IR");
        Path output = Path.of("build/probe-ts-script-input-shapes/annotated");
        var binding = BindingCatalogEntry.of("builder", ScriptType.SERVER, PostBuilder.class, false);
        var inheritedBinding = BindingCatalogEntry.of("inheritedBuilder", ScriptType.SERVER,
                InheritedPostBuilder.class, false);
        Map<String, String> files = new TypeScriptProbeBackend().render(
                context(output, List.of(), List.of(binding, inheritedBinding)));
        String module = files.get("@package/com/tkisor/nekojs/probe/testfixture/input/index.d.ts");
        assertTrue(module.contains("extends $ScriptInputFixtures$PostBuilder"), module);
        assertTrue(module.contains("import { $EventTypes$Key"), module);
        assertTrue(files.get("@package/othermod/events/index.d.ts").contains("name: string"));
        writeFixture(files, output, """
                import type { $ScriptInputFixtures$PostBuilder, $ScriptInputFixtures$InheritedPostBuilder } from 'java:com/tkisor/nekojs/probe/testfixture/input';
                declare const builder: $ScriptInputFixtures$PostBuilder;
                declare const inherited: $ScriptInputFixtures$InheritedPostBuilder;
                inherited.configure({ format: 'precise', labels: { source: 'inherited' } });
                // @ts-expect-error Inherited input metadata must not widen to the reflected Map.
                inherited.configure({ format: 'fast', unknown: true });
                // @ts-expect-error Inherited provider fields remain required.
                inherited.configure({ labels: { source: 'inherited' } });
                builder.configure({ format: 'fast', labels: { source: 'test' }, target: 'key' });
                // @ts-expect-error A broad reflected Map must not bypass the provider shape.
                builder.configure({ format: 'fast', unknown: true });
                // @ts-expect-error Required fields remain required.
                builder.configure({ labels: { source: 'test' } });
                // @ts-expect-error Literal values remain constrained.
                builder.configure({ format: 'unknown' });
                // @ts-expect-error Raw Record values preserve their string value type.
                builder.configure({ format: 'fast', labels: { source: 42 } });
                """);
    }

    @Test
    void providerRequiresAPublicNoArgumentConstructor() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new TypeReflector().reflect(InvalidConsumer.class));
        assertTrue(failure.getMessage().contains("NEKO-4063"), failure.getMessage());
        assertInstanceOf(NoSuchMethodException.class, failure.getCause());
    }

    @Test
    void annotatedRegistryShapesImportTheSharedRegistryNamespace() {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        generator.predeclareClass(RegistryConsumer.class.getName(), new TypeReflector().reflect(RegistryConsumer.class), java.util.Set.of());
        String declaration = generator.generate(RegistryConsumer.class.getPackageName(),
                List.of("ScriptInputFixtures$RegistryConsumer"), List.of(), java.util.Set.of(RegistryConsumer.class.getName()));
        assertTrue(declaration.contains("import type { RegistryTypes } from \"@special/types\";"), declaration);
        assertTrue(declaration.contains(": RegistryTypes.Item"), declaration);
    }

    private static ProbeContext context(Path output, List<EventCatalogEntry> events, List<BindingCatalogEntry> bindings) {
        var adapter = new AdapterCatalogEntry(othermod.events.EventTypes.Key.class,
                List.of(self(), literal("key")), ConversionPrecedence.HIGH, Optional.empty());
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), bindings, events, List.of(adapter),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), List.of(), List.of(), 1, "SMART"));
        return new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of());
    }

    private static void writeFixture(Map<String, String> files, Path output, String exercise) throws Exception {
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/server/events/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), exercise);
    }
}
