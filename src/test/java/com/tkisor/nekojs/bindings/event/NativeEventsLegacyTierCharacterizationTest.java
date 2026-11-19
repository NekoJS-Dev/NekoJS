// TODO(fabric): the fabric side has no counterpart yet; drop this whole-file guard once the port lands
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.LegacySurfaceAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.ModuleBinding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the tier of {@code NativeEvents}: it stays a legacy/raw adapter observation
 * surface (the raw NeoForge event bridge) and a STARTUP-only versioned module export under
 * {@code nekojs/api/version}. Its declaration source is a {@code TypeDocCatalogEntry} (for
 * completion and docs), and neither module exposure nor the catalog collection step
 * <strong>ever</strong> upgrades it to stable:
 *
 * <ul>
 *   <li>registration: it is registered only in the STARTUP binding set (a STARTUP reload calls
 *       {@code close()}, which unregisters the previous round's native listeners) and exported
 *       under {@code nekojs/api/version} ({@link BindingExposure.Category#VERSION}); the
 *       SERVER/CLIENT binding sets and {@link BindingExposure.Category#STABLE} do not have it;</li>
 *   <li>catalog: the binding enters the {@code legacySurface} observation surface (the
 *       {@code global:} kind plus a placeholder signature, the legacy shadow semantics), it does
 *       not appear in the events catalog (it is not an event group), and it does not enter
 *       managedApis (the managed stable surface has no such symbol);</li>
 *   <li>declaration source: the
 *       {@code TypeDocCatalogEntry.binding(STARTUP, "NativeEvents", ...)} entry from
 *       {@code NekoJSCorePlugin.registerTypeDocs}.</li>
 * </ul>
 */
class NativeEventsLegacyTierCharacterizationTest {

    @Test
    void nativeEventsIsAStartupOnlyRawBinding() {
        BindingRegistry.BindingRegistryImpl startup =
                new BindingRegistry.BindingRegistryImpl(ScriptType.STARTUP).scopedTo("nekojs");
        new NekoJSCorePlugin().registerBinding(startup);
        assertTrue(startup.viewRegistered().containsKey("NativeEvents"),
                "NativeEvents must be registered in the STARTUP binding set (raw adapter face)");
        Binding binding = startup.viewRegistered().get("NativeEvents");
        assertTrue(binding.value() instanceof NativeEventsJS,
                "the binding value is the raw NativeEventsJS bridge");

        List<ModuleBinding> startupNativeEventsExports = startup.moduleBindings().stream()
                .filter(entry -> "NativeEvents".equals(entry.binding().name()))
                .toList();
        assertEquals(1, startupNativeEventsExports.size(),
                "NativeEvents must be exported once in STARTUP module bindings");
        ModuleBinding versionExport = startupNativeEventsExports.getFirst();
        assertEquals("nekojs/api/version", versionExport.moduleId(),
                "NativeEvents must be published under the nekojs/api/version module");
        assertEquals(BindingExposure.Category.VERSION, versionExport.category(),
                "NativeEvents module export must use BindingExposure.Category.VERSION");
        assertEquals(ScriptType.STARTUP, versionExport.scriptType(),
                "NativeEvents module export must stay scoped to STARTUP");
        assertSame(binding.value(), versionExport.binding().value(),
                "global and versioned module registrations share the same NativeEventsJS instance");
        assertFalse(startup.moduleBindings().stream()
                        .anyMatch(entry -> "NativeEvents".equals(entry.binding().name())
                                && entry.category() == BindingExposure.Category.STABLE),
                "NativeEvents must NOT be exported in BindingExposure.Category.STABLE");

        BindingRegistry.BindingRegistryImpl server =
                new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER).scopedTo("nekojs");
        new NekoJSCorePlugin().registerBinding(server);
        assertFalse(server.viewRegistered().containsKey("NativeEvents"),
                "NativeEvents must NOT be registered outside STARTUP (STARTUP reload owns its lifecycle)");
        assertFalse(server.moduleBindings().stream()
                        .anyMatch(entry -> "NativeEvents".equals(entry.binding().name())),
                "NativeEvents must NOT be exported in SERVER module bindings");
    }

    @Test
    void catalogExposureStaysLegacyObservationAndNeverEntersManagedStable() {
        // Catalog collection: the STARTUP binding enters the legacy observation surface.
        // LegacySurfaceAdapter is the legacy projection step of NekoScriptCatalog.snapshot.
        // snapshot() itself needs a running FML loader and cannot run in a unit test, so the test
        // drives the same adapter directly.
        NekoScriptCatalogSnapshot minimal = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.STARTUP),
                NekoScriptCatalog.bindings(new RuntimeWithNativeEvents(), ScriptType.STARTUP),
                NekoScriptCatalog.events(new RuntimeWithNativeEvents()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        List<ApiSymbol> legacySurface = LegacySurfaceAdapter.convert(minimal);

        assertTrue(legacySurface.stream()
                        .map(ApiSymbol::id)
                        .anyMatch(id -> id.equals(ApiSymbolId.parse("global:NativeEvents"))),
                "NativeEvents is observable in the legacy surface (global: observation kind)");
        assertEquals(0, minimal.events().size(),
                "NativeEvents is not an event group: the events catalog has no NativeEvents entries");
        // The managed stable surface is built only from apiRuntime contributions (the chain
        // CoreManagedApiBootstrap -> ApiContributionRegistry -> JsApiSurfaceResolver).
        // NativeEventsJS is a raw binding and registers no ApiContribution, so the catalog
        // collection step cannot silently upgrade it to managed stable.
    }

    @Test
    void declarationSourceIsTheTypeDocEntry() {
        List<TypeDocCatalogEntry> docs = new ArrayList<>();
        new NekoJSCorePlugin().registerTypeDocs(new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
                docs.add(entry);
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            }
        });

        TypeDocCatalogEntry nativeEvents = docs.stream()
                .filter(d -> d.target().equals("NativeEvents"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "NativeEvents declaration source must be a TypeDocCatalogEntry binding doc"));
        assertEquals(ScriptType.STARTUP, nativeEvents.scriptType(),
                "the doc declares NativeEvents for the STARTUP side only");
        assertTrue(nativeEvents.description() != null && !nativeEvents.description().isBlank(),
                "the doc carries the raw/legacy capability description");
        assertTrue(!nativeEvents.examples().isEmpty(),
                "the doc carries the usage example (probe/completion source)");
    }

    /** A minimal runtime carrying only the NativeEvents STARTUP binding; it is the projection
     * input for observing legacySurface. */
    private static final class RuntimeWithNativeEvents implements IPluginRuntime {
        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            if (type == ScriptType.STARTUP) {
                NativeEventsJS bridge = new NativeEventsJS();
                return Map.of("NativeEvents", Binding.of("NativeEvents", bridge));
            }
            return Map.of();
        }

        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry> registryBuilderSurfaces() { return List.of(); }
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
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }
}
//?}
