package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptEventImportTest {
    public static class PayloadHost { public boolean hiddenHostMember; }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void sameNamedEventsAndDispatchAdaptersKeepTheirJavaIdentity() throws Exception {
        var first = EventCatalogEntry.of("IdentityEvents", "first", ScriptType.SERVER,
                othermod.events.EventTypes.Payload.class, othermod.events.EventTypes.Key.class, false, true);
        var second = EventCatalogEntry.of("IdentityEvents", "second", ScriptType.SERVER,
                othermod.events.second.EventTypes.Payload.class, othermod.events.second.EventTypes.Key.class, false, true);
        var arrays = EventCatalogEntry.of("IdentityEvents", "arrays", ScriptType.SERVER,
                othermod.events.second.EventTypes.Payload.class, othermod.events.second.EventTypes.Key[].class, false, true);
        List<AdapterCatalogEntry> adapters = List.of(adapter(othermod.events.EventTypes.Key.class, "first"),
                adapter(othermod.events.second.EventTypes.Key.class, "second"));
        var backend = new TypeScriptProbeBackend();
        Path output = Path.of("build/probe-ts-event-imports/collisions");
        Map<String, String> files = backend.render(context(output, List.of(first, second, arrays), adapters));
        write(files, output);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/server/events/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $EventTypes$Key as FirstKey } from 'java:othermod/events';
                import type { $EventTypes$Key as SecondKey } from 'java:othermod/events/second';
                declare const first: FirstKey;
                declare const second: SecondKey;
                IdentityEvents.first(first, event => {
                    const count: number = event.count;
                    // @ts-expect-error The other event's fields must remain unavailable.
                    event.text;
                });
                IdentityEvents.second(second, event => {
                    const text: string = event.text;
                    // @ts-expect-error An import collision must not substitute the first payload.
                    event.count;
                });
                IdentityEvents.first('first', event => {});
                IdentityEvents.second('second', event => {});
                IdentityEvents.arrays([second, 'second'], event => { const text: string = event.text; });
                // @ts-expect-error Dispatch adapter aliases retain their Java identity.
                IdentityEvents.first(second, event => {});
                // @ts-expect-error Literal inputs must use the matching adapter.
                IdentityEvents.second('first', event => {});
                // @ts-expect-error Array dispatch inputs use the same local import mapping.
                IdentityEvents.arrays([first], event => {});
                """);
        String events = files.get("@side-only/server/events/index.d.ts");
        assertTrue(events.contains("$EventTypes$Payload as "), events);
        assertTrue(events.contains("$EventTypes$Key_ as "), events);
        String reversed = backend.render(context(output, List.of(arrays, second, first), adapters))
                .get("@side-only/server/events/index.d.ts");
        assertEquals(imports(events), imports(reversed));
        String reused = backend.render(context(output, List.of(first), List.of(adapters.getFirst())))
                .get("@side-only/server/events/index.d.ts");
        assertTrue(reused.contains("import { $EventTypes$Key, $EventTypes$Key_, $EventTypes$Payload } from \"java:othermod/events\";"), reused);
        assertFalse(reused.contains("$NekoImport"), reused);
    }

    private static AdapterCatalogEntry adapter(Class<?> type, String literal) {
        return new AdapterCatalogEntry(type, List.of(AdapterInputShape.self(), AdapterInputShape.literal(literal)),
                ConversionPrecedence.HIGH, Optional.empty());
    }

    @Test
    void localBuilderPayloadNamesDoNotShadowReflectedEvents() throws Exception {
        var reflected = EventCatalogEntry.of("PayloadEvents", "reflected", ScriptType.SERVER,
                othermod.events.EventTypes.Payload.class, null, false, false);
        var scripted = EventCatalogEntry.of("PayloadEvents", "scripted", ScriptType.SERVER,
                PayloadHost.class, null, false, false);
        var custom = EventCatalogEntry.ofScriptEvent("PayloadEvents", "custom", ScriptType.SERVER);
        var payload = new RegistryBuilderSurfaceEntry.EventPayload(PayloadHost.class, "$EventTypes$Payload", List.of(
                new RegistryBuilderSurfaceEntry.Member("accepted", RegistryBuilderSurfaceEntry.MemberKind.READ_ONLY_PROPERTY,
                        "boolean", "bool")));
        var builder = new RegistryBuilderSurfaceEntry("IdentityBuilder", "test:identity", "basic", "identity",
                List.of(), "", payload);
        Path output = Path.of("build/probe-ts-event-imports/payloads");
        var context = context(output, List.of(reflected, scripted, custom), List.of(), List.of(builder));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        write(files, output);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/server/events/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                PayloadEvents.reflected(event => {
                    const count: number = event.count;
                    // @ts-expect-error Local payload names must not replace reflected types.
                    event.accepted;
                });
                PayloadEvents.scripted(event => {
                    const accepted: boolean = event.accepted;
                    // @ts-expect-error Builder payloads retain their read-only contract.
                    event.accepted = false;
                    // @ts-expect-error Replaced payloads do not expose host fields.
                    event.hiddenHostMember;
                });
                PayloadEvents.custom(payload => {});
                PayloadEvents.custom.post({ custom: 12 });
                """);
        String events = files.get("@side-only/server/events/index.d.ts");
        assertTrue(events.contains("$EventTypes$Payload as "), events);
        assertTrue(events.contains("interface $EventTypes$Payload {"), events);
        assertFalse(events.contains("$TypeScriptEventImportTest$PayloadHost"), events);
        assertFalse(files.containsKey("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts"));
    }

    private static ProbeContext context(Path output, List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters) {
        return context(output, events, adapters, List.of());
    }

    private static ProbeContext context(Path output, List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
                                        List<RegistryBuilderSurfaceEntry> builders) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), events, adapters,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), builders, List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), List.of(), List.of(), 1, "SMART"));
        return new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of());
    }

    private static List<String> imports(String declaration) {
        return declaration.lines().filter(line -> line.startsWith("import ")).toList();
    }

    private static void write(Map<String, String> files, Path output) throws Exception {
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
    }
}
