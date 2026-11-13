package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * NekoRuntimeAccess is the single process-level plugin runtime slot, so this test repeats the same
 * checks in any order: set/get returns the same instance, the event methods route to the current
 * instance, and routing follows a replacement. The slot is not a second runtime owner; in
 * production only {@code NekoPluginRuntime.publish} writes it, once.
 */
class NekoRuntimeAccessTest {

    static final class CountingRuntime implements IPluginRuntime {
        final AtomicInteger init = new AtomicInteger();
        final AtomicInteger initStartup = new AtomicInteger();
        final AtomicInteger afterInit = new AtomicInteger();

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() { init.incrementAndGet(); }
        @Override public void fireInitStartup() { initStartup.incrementAndGet(); }
        @Override public void fireAfterInit() { afterInit.incrementAndGet(); }
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }

    @Test
    void accessSlotRoutesEventsToTheSingleProcessRuntime() {
        // The slot is a process singleton, so the test must restore it or the case order leaks slot
        // state into other tests: a repeatable test also covers the test itself
        IPluginRuntime previous = NekoRuntimeAccess.get();
        try {
        CountingRuntime first = new CountingRuntime();
        NekoRuntimeAccess.set(first);

        assertSame(first, NekoRuntimeAccess.get(), "get() must return the installed runtime instance");

        NekoRuntimeAccess.get().fireInit();
        NekoRuntimeAccess.get().fireInitStartup();
        NekoRuntimeAccess.get().fireAfterInit();
        assertEquals(1, first.init.get(), "fireInit must route to the process runtime exactly once per call");
        assertEquals(1, first.initStartup.get());
        assertEquals(1, first.afterInit.get());

        // The slot takes a replacement: after set, the event methods route to the new instance
        // (in production, reload publishes the new one)
        CountingRuntime second = new CountingRuntime();
        NekoRuntimeAccess.set(second);
        assertSame(second, NekoRuntimeAccess.get());

        NekoRuntimeAccess.get().fireInit();
        assertEquals(1, second.init.get());
        assertEquals(1, first.init.get(), "replaced runtime must not receive further events");
        } finally {
            NekoRuntimeAccess.set(previous);
        }
    }
}
