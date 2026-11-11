//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.bindings.static_access.ClientUiJS;
import com.tkisor.nekojs.core.compiler.NodeModuleTypeDocs;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeJsxProbeDeclarationTest {
    @BeforeAll
    static void initializePlatform() {
        try {
            com.tkisor.nekojs.platform.Platform.instance();
            return;
        } catch (IllegalStateException uninitialized) {
            // The declaration backend also runs without the game's loader bootstrap.
        }
        com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
            public boolean isClient() { return false; }
            public boolean isDevelopment() { return true; }
            public String getMcVersion() { return "0.0.0"; }
            public String getLoaderId() { return "test"; }
            public String getLoaderVersion() { return "0"; }
            public Path getGameDir() { return com.tkisor.nekojs.TestGameDirs.unique("nekojs-probe-jsx"); }
            public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
            public com.tkisor.nekojs.platform.IModInfo getInfo(String modId) { return null; }
        });
    }

    @Test
    void actualClientHostProducesStructurallyCompatibleDeclarations() throws Exception {
        Path output = Path.of("build/probe-ts-native-jsx");
        var binding = BindingCatalogEntry.of("ClientUI", ScriptType.CLIENT, ClientUiJS.class, false);
        var manual = new java.util.ArrayList<ManualDeclarationCatalogEntry>();
        var registry = new com.tkisor.nekojs.core.plugin.TypeDocsRegister() {
            public void register(TypeDocCatalogEntry entry) {}
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) { manual.add(entry); }
        };
        new com.tkisor.nekojs.core.NekoJSCorePlugin().registerTypeDocs(registry);
        NodeModuleTypeDocs.registerBuiltin(registry);
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(binding), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), manual, List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.client.ui"), List.of(), List.of(), List.of(), 1, "SMART"));
        List<Class<?>> types = List.of(ClientUiJS.class, JsxHostAdapter.class);
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output), "typescript", output,
                types.stream().map(type -> new TypeReflector().reflect(type)).toList());
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String declarations = files.get("@package/com/tkisor/nekojs/client/ui/index.d.ts");
        assertNotNull(declarations);
        assertTrue(declarations.contains("readonly width: number; readonly height: number"), declarations);
        assertTrue(declarations.contains("import('nekojs/jsx-runtime').NekoUiViewportInput"), declarations);
        for (var file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        // Match the installed editor profile: strict consumers, without checking the entire Minecraft declaration graph.
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":true,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","@side-only/client/bindings/index.d.ts","@manual/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { UI, type NekoUiHostAdapter } from 'nekojs/jsx-runtime';
                const host = ClientUI.screen('Probe UI', false);
                const adapter: NekoUiHostAdapter = host;
                const region: NekoUiHostAdapter = ClientUI.region(0, 0, 320, 240);
                const root = UI.createRoot(() => null, host);
                host.bindRoot(root);
                const width: number = host.viewport().width;
                const measured: number = host.measureText('text', 9, 100).height;
                const mapValue: unknown = host.viewport().get('width');
                // @ts-expect-error Measurements are numeric.
                const invalid: string = host.measureText('text', 9, 100).width;
                // @ts-expect-error Viewport dimensions remain numeric.
                const invalidViewport: string = host.viewport().width;
                // @ts-expect-error Viewport dimensions describe an immutable snapshot.
                host.viewport().width = 1;
                // @ts-expect-error Native layout requires viewport and snapshot arguments.
                adapter.layout([]);
                // @ts-expect-error Render callbacks must return renderable values.
                UI.createRoot(() => ({ invalid: true }), host);
                """);
    }
}
//?}
