package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.facade.I18nFacade;
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

class I18nProbeDeclarationTest {
    @Test
    void actualClientBindingProducesAStringReturningVariadicDeclaration() throws Exception {
        TestPlatformInit.ensureInitialized();
        Path output = Path.of("build/probe-ts-i18n");
        var docs = new java.util.ArrayList<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry>();
        new com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin().registerTypeDocs(new com.tkisor.nekojs.core.plugin.TypeDocsRegister() {
            public void register(com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry entry) { docs.add(entry); }
            public void registerManualDeclaration(com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry entry) { fail("Unexpected manual declaration"); }
        });
        var binding = BindingCatalogEntry.of("I18n", ScriptType.CLIENT, I18nFacade.class, false).withDoc(docs.getFirst());
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(binding), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.api.facade"), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(I18nFacade.class), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, List.of(new TypeReflector().reflect(I18nFacade.class)));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String declarations = files.get("@package/com/tkisor/nekojs/api/facade/index.d.ts");
        assertTrue(declarations.contains("translate(key: string, args?: unknown[]): string;"), declarations);
        String clientBindings = files.get("@side-only/client/bindings/index.d.ts");
        assertTrue(clientBindings.contains("let I18n: { translate(key: string, ...args: (string | number | boolean | null)[]): string;"));
        assertTrue(clientBindings.contains("has(key: string): boolean; translateWithFallback(key: string, fallback: string, ...args: (string | number | boolean | null)[]): string;"));
        assertTrue(clientBindings.contains("readonly languageCode: string; readonly rightToLeft: boolean"));
        assertFalse(files.getOrDefault("@side-only/server/bindings/index.d.ts", "").contains("let I18n:"));
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/client/bindings/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                const title: string = I18n.translate("example.title");
                const count: string = I18n.translate("example.count", 4, "text", true, null);
                const exists: boolean = I18n.has("example.title");
                const fallback: string = I18n.translateWithFallback("example.count", "Count %s", 4, true, null);
                const language: string = I18n.languageCode;
                const rtl: boolean = I18n.rightToLeft;
                // @ts-expect-error Language metadata is read-only.
                I18n.languageCode = "en_us";
                // @ts-expect-error Text direction is read-only.
                I18n.rightToLeft = true;
                // @ts-expect-error Membership keys must be strings.
                I18n.has(4);
                // @ts-expect-error Fallback text is required.
                I18n.translateWithFallback("missing");
                // @ts-expect-error Arbitrary objects are not fallback format arguments.
                I18n.translateWithFallback("missing", "%s", {});
                // @ts-expect-error Translation keys must be strings.
                I18n.translate(4);
                // @ts-expect-error Translation resolves to plain text.
                const wrong: number = I18n.translate("example.title");
                // @ts-expect-error Arbitrary objects are not localization format arguments.
                I18n.translate("example.title", {});
                void title; void count; void wrong; void exists; void fallback; void language; void rtl;
                """);
    }
}
