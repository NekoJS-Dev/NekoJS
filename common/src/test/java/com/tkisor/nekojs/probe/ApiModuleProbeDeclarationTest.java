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
        var event = new ModuleEventCatalogEntry("nekojs/api/stable/addon", "addon", BindingExposure.Category.STABLE,
                ScriptType.SERVER, EventCatalogEntry.of("Changes", "changed", ScriptTypePredicate.exact(ScriptType.SERVER),
                NekoId.class, null, false, false));
        var tick = new ModuleEventCatalogEntry("nekojs/api/stable", "nekojs", BindingExposure.Category.STABLE,
                ScriptType.SERVER, EventCatalogEntry.of("ServerEvents", "tickPre", ScriptTypePredicate.exact(ScriptType.SERVER),
                com.tkisor.nekojs.api.event.StableServerTickEvent.class, null, false, false));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(ScriptType.SERVER), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of(),
                List.of(), List.of(), List.of(binding), List.of(event, tick));
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.api"), List.of(), List.of(), List.of(), 1, "SMART"));
        var reflector = new TypeReflector();
        var context = new ProbeContext.Of(snapshot, List.of(IdFacade.class, NekoId.class, com.tkisor.nekojs.api.event.StableServerTickEvent.class), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of(reflector.reflect(IdFacade.class), reflector.reflect(NekoId.class), reflector.reflect(com.tkisor.nekojs.api.event.StableServerTickEvent.class)));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        var baseline = ApiModuleBaseline.fromJson(files.get("@special/api-modules.json"));
        assertEquals(3, baseline.exports().size());
        assertTrue(baseline.exports().get("SERVER:nekojs/api/stable:ID").facets().stream()
                .anyMatch(facet -> facet.startsWith("call:resource(")));
        String server = files.get("@side-only/server/bindings/api-modules.d.ts");
        assertTrue(server.contains("export const ID:"), server);
        assertTrue(server.contains("export namespace Changes"), server);
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
                import { ID, ServerEvents } from 'nekojs/api/stable';
                import { Changes } from 'nekojs/api/stable/addon';
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
                function requiredTick() { require("nekojs/api/stable").ServerEvents.tickPre(e => { const n: number = e.tick; void n; }); }
                const id = ID.resource('minecraft:stone');
                const text: string = ID.asString(id);
                const required = require('nekojs/api/stable');
                const same: string = required.ID.asString(required.ID.of('stone'));
                Changes.changed(event => { const value: string = event.asString(); void value; });
                // @ts-expect-error The known CommonJS module is not any.
                required.ID.absent();
                // @ts-expect-error Unknown require results must be narrowed.
                require('unknown-module').absent();
                // @ts-expect-error Resource ID input is a string.
                ID.resource(4);
                // @ts-expect-error A module-only export does not create a global.
                ChangesNotGlobal.changed(() => {});
                void text; void same;
                """);
    }
}
