package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.catalog.*;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.facade.IdFacade;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiModuleProbeDeclarationTest {
    @Test
    void modulesUseThePublicBindingAndPayloadTypesForEsmAndRequire() throws Exception {
        TestPlatformInit.ensureInitialized();
        Path output = Path.of("build/probe-ts-api-modules");
        var binding = new ModuleBindingCatalogEntry("nekojs/api/stable", "nekojs", BindingExposure.Category.STABLE,
                BindingCatalogEntry.of("ID", ScriptType.SERVER, IdFacade.class, false));
        var pluginBinding = new ModuleBindingCatalogEntry("nekojs/plugins/events/api/stable", "events", BindingExposure.Category.STABLE,
                BindingCatalogEntry.of("ID", ScriptType.SERVER, IdFacade.class, false));
        var event = new ModuleEventCatalogEntry("nekojs:event/stable/addon", "addon", BindingExposure.Category.STABLE,
                ScriptType.SERVER, EventCatalogEntry.of("ServerEvents", "tickPre", ScriptTypePredicate.exact(ScriptType.SERVER),
                NekoId.class, null, false, false));
        var tick = new ModuleEventCatalogEntry("nekojs:event/stable", "nekojs", BindingExposure.Category.STABLE,
                ScriptType.SERVER, EventCatalogEntry.of("ServerEvents", "tickPre", ScriptTypePredicate.exact(ScriptType.SERVER),
                com.tkisor.nekojs.api.event.StableServerTickEvent.class, null, false, false));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(ScriptType.SERVER), List.of(), List.of(tick.event(),
                EventCatalogEntry.ofScriptEvent("CustomEvents", "changed", ScriptType.SERVER)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of(),
                List.of(), List.of(), List.of(binding, pluginBinding), List.of(event, tick));
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.api"), List.of(), List.of(), List.of(), 1, "SMART"));
        var reflector = new TypeReflector();
        var context = new ProbeContext.Of(snapshot, List.of(IdFacade.class, NekoId.class, com.tkisor.nekojs.api.event.StableServerTickEvent.class), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of(reflector.reflect(IdFacade.class), reflector.reflect(NekoId.class), reflector.reflect(com.tkisor.nekojs.api.event.StableServerTickEvent.class)));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        var baseline = ApiModuleBaseline.fromJson(files.get("@special/api-modules.json"));
        assertEquals(4, baseline.exports().size());
        assertTrue(baseline.exports().containsKey("SERVER:nekojs/plugins/events/api/stable:ID"));
        assertFalse(baseline.exports().containsKey("SERVER:nekojs:event/stable:ID"));
        assertTrue(LegacySurfaceAdapter.convert(snapshot).stream().anyMatch(symbol ->
                symbol.id().value().equals("module:nekojs/plugins/events/api/stable:ID")));
        assertTrue(baseline.exports().get("SERVER:nekojs/api/stable:ID").facets().stream()
                .anyMatch(facet -> facet.startsWith("call:resource(")));
        String server = files.get("@side-only/server/bindings/api-modules.d.ts");
        assertTrue(server.contains("export const ID:"), server);
        assertTrue(server.contains("declare module \"nekojs:event/stable/addon\""), server);
        assertTrue(baseline.exports().containsKey("SERVER:nekojs:event/stable:ServerEvents.tickPre"));
        assertTrue(baseline.exports().containsKey("SERVER:nekojs:event/stable/addon:ServerEvents.tickPre"));
        String globals = files.getOrDefault("@side-only/server/events/index.d.ts", "");
        assertFalse(globals.contains("ServerEvents"), globals);
        assertTrue(globals.contains("CustomEvents"), globals);
        assertFalse(LegacySurfaceAdapter.convert(snapshot).stream().anyMatch(symbol ->
                symbol.id().kind().equals("event") && symbol.id().qualifiedName().startsWith("ServerEvents.")));
        assertFalse(files.getOrDefault("@side-only/client/bindings/api-modules.d.ts", "").contains("export const ID:"));
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/server/bindings/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { ID } from 'nekojs/api/stable';
                import { ID as PluginID } from 'nekojs/plugins/events/api/stable';
                // @ts-expect-error Official event modules do not aggregate plugin helper Bindings.
                import { ID as EventModuleID } from 'nekojs:event/stable';
                // @ts-expect-error Plugin Binding modules do not aggregate official event groups.
                import { ServerEvents as PluginBindingEvents } from 'nekojs/plugins/events/api/stable';
                import { ServerEvents } from 'nekojs:event/stable';
                import { ServerEvents as AddonEvents } from 'nekojs:event/stable/addon';
                // @ts-expect-error Events are not part of the Binding aggregate.
                import { ServerEvents as OldServerEvents } from 'nekojs/api/stable';
                // @ts-expect-error Plugin events use the reserved plugin event route.
                import { ServerEvents as OldChanges } from 'nekojs/api/stable/addon';
                ServerEvents.tickPre(event => {
                    const tick: number = event.tick;
                    // @ts-expect-error Stable tick snapshots are read-only.
                    event.tick = tick + 1;
                    // @ts-expect-error No native server leaks into stable payloads.
                    event.server;
                });
                // @ts-expect-error No unaudited native event is in the stable group.
                ServerEvents.starting(() => {});
                requiredTick();
                function requiredTick() { require("nekojs:event/stable").ServerEvents.tickPre(e => { const n: number = e.tick; void n; }); }
                const id = ID.resource('minecraft:stone');
                const text: string = ID.asString(id);
                const pluginText: string = PluginID.asString(PluginID.of("stone"));
                const pluginRequired = require("nekojs/plugins/events/api/stable");
                const requiredPluginText: string = pluginRequired.ID.asString(pluginRequired.ID.of("stone"));
                // @ts-expect-error Known plugin require results retain their helper types.
                pluginRequired.ID.absent();
                // @ts-expect-error Official event routes do not alias the owner named events.
                require("nekojs:event/stable").ID;
                // @ts-expect-error Plugin Binding aggregates do not export event groups.
                pluginRequired.ServerEvents;
                const required = require('nekojs/api/stable');
                const same: string = required.ID.asString(required.ID.of('stone'));
                AddonEvents.tickPre(event => {
                    const value: string = event.asString();
                    // @ts-expect-error Owner collisions do not substitute the official tick payload.
                    event.tick;
                    void value;
                });
                // @ts-expect-error The known CommonJS module is not any.
                required.ID.absent();
                // @ts-expect-error Unknown require results must be narrowed.
                require('unknown-module').absent();
                // @ts-expect-error Resource ID input is a string.
                ID.resource(4);
                // @ts-expect-error A module-only export does not create a global.
                globalThis.AddonEvents.tickPre(() => {});
                // @ts-expect-error Native event groups are not globals.
                globalThis.ServerEvents.tickPre(() => {});
                // @ts-expect-error CommonJS Binding aggregates do not expose event groups.
                require("nekojs/api/stable").ServerEvents;
                void text; void same; void pluginText; void requiredPluginText;
                """);
    }
}
