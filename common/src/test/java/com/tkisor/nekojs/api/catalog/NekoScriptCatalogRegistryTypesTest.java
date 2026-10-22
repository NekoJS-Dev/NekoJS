package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NekoScriptCatalogRegistryTypesTest {
    public static class Entry { public String label; }
    public static class Use { public void accept(Entry value) {} }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void fullSnapshotUsesPublishedAdaptersOnce(@TempDir Path directory) throws Exception {
        writeFixture(null, directory);
    }

    @Test
    void sideSnapshotUsesPublishedAdaptersOnce(@TempDir Path directory) throws Exception {
        writeFixture(ScriptType.SERVER, directory);
    }

    @Test
    void existingProviderWithoutAdapterOverloadRemainsUsable() {
        var calls = new AtomicInteger();
        var expected = List.of(new RegistryTypeCatalogEntry("Existing", List.of("fixture:old"), List.of()));
        NekoScriptCatalog.setPlatformProvider(new NekoCatalogPlatformProvider() {
            @Override public Collection<RegistryTypeCatalogEntry> registryTypes() {
                calls.incrementAndGet();
                return expected;
            }
        });
        try {
            assertEquals(expected, NekoScriptCatalog.snapshot(new RuntimeStub(List.of())).registryTypes());
            assertEquals(1, calls.get());
        } finally {
            NekoScriptCatalog.setPlatformProvider(null);
        }
    }

    private static void writeFixture(ScriptType side, Path directory) throws Exception {
        List<JSTypeAdapter<?>> adapters = List.of(new AbstractJSTypeAdapter<Entry>() {
            @Override public Class<Entry> getTargetClass() { return Entry.class; }
            @Override public List<AdapterInputShape> inputShapes() {
                return List.of(AdapterInputShape.self(), AdapterInputShape.registry("FixtureEntry"));
            }
            @Override protected Entry fromString(String value) { return null; }
            @Override protected Entry fromHostObject(Object value) {
                return value instanceof Entry entry ? entry : null;
            }
        });
        var calls = new AtomicInteger();
        NekoScriptCatalog.setPlatformProvider(new NekoCatalogPlatformProvider() {
            @Override public Collection<RegistryTypeCatalogEntry> registryTypes() {
                return List.of(new RegistryTypeCatalogEntry("Existing", List.of("fixture:old"), List.of()));
            }
            @Override public Collection<RegistryTypeCatalogEntry> registryTypes(List<JSTypeAdapter<?>> actual) {
                assertSame(adapters, actual);
                calls.incrementAndGet();
                return List.of(new RegistryTypeCatalogEntry("Existing", List.of("fixture:old"), List.of()),
                        new RegistryTypeCatalogEntry("FixtureEntry", List.of("fixture:one", "fixture:two"),
                                List.of("fixture:group")));
            }
        });
        try {
            var runtime = new RuntimeStub(adapters);
            NekoScriptCatalogSnapshot snapshot = side == null ? NekoScriptCatalog.snapshot(runtime)
                    : NekoScriptCatalog.snapshot(runtime, side);
            var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                    List.of("com.tkisor.nekojs.api.catalog"), List.of(), List.of(), List.of(), 3, "SMART"));
            List<Class<?>> types = List.of(Entry.class, Use.class);
            var context = new ProbeContext.Of(snapshot, types, config,
                    NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve("types"),
                    types.stream().map(type -> new TypeReflector().reflect(type)).toList());
            Map<String, String> files = new TypeScriptProbeBackend().render(context);
            Map<String, String> python = new PythonProbeBackend().render(context);
            Path output = Path.of("build/probe-ts-registry-catalog", side == null ? "full" : "server");
            for (var entry : files.entrySet()) {
                Path target = output.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue());
            }
            Files.writeString(output.resolve("exercise.ts"), """
                    import type { $NekoScriptCatalogRegistryTypesTest$Use as Use } from 'java:com/tkisor/nekojs/api/catalog';
                    import type { RegistryTypes } from '@special/types';
                    declare const use: Use;
                    use.accept('fixture:one');
                    use.accept('fixture:two');
                    const tag: RegistryTypes.FixtureEntryTag = 'fixture:group';
                    // @ts-expect-error Registry metadata retains its finite value set.
                    use.accept('fixture:missing');
                    // @ts-expect-error Tags are collected independently of values.
                    const badTag: RegistryTypes.FixtureEntryTag = 'fixture:one';
                    """);
            Files.writeString(output.resolve("tsconfig.json"), """
                    {"compilerOptions":{"strict":true,"skipLibCheck":false,"noEmit":true,"target":"ES2022",
                    "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                    "paths":{"java:*": ["./@package/*"],"@special/*":["./@special/*"]}},
                    "include":["exercise.ts","@package/**/*.d.ts","@special/**/*.d.ts"]}
                    """);
            assertEquals(1, calls.get());
            assertEquals(List.of("Existing", "FixtureEntry"), snapshot.registryTypes().stream()
                    .map(RegistryTypeCatalogEntry::typeName).toList());
            String pythonModule = python.get("nekojs/_java/com/tkisor/nekojs/api/catalog/__init__.pyi");
            assertTrue(pythonModule.contains("Literal[\"fixture:one\", \"fixture:two\"]"), pythonModule);
        } finally {
            NekoScriptCatalog.setPlatformProvider(null);
        }
    }

    private record RuntimeStub(List<JSTypeAdapter<?>> adapters) implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId id) { return null; }
    }
}
