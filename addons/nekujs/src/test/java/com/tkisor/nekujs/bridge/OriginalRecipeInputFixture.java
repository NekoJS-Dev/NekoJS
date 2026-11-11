package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.js.type_adapter.FluidStackAdapter;
import com.tkisor.nekojs.js.type_adapter.IngredientAdapter;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.SizedFluidIngredientAdapter;
import com.tkisor.nekojs.js.type_adapter.SizedIngredientAdapter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import java.util.List;

/** Reuses real native converters and unchanged addon wrapper registrations in runtime and declaration fixtures. */
public final class OriginalRecipeInputFixture {
    private OriginalRecipeInputFixture() {}

    public static JSTypeAdapterRegistry adapters(KubeJSPlugin originalCreatePlugin) {
        var registry = new JSTypeAdapterRegistry.Impl();
        LegacyTypeWrappers.register(registry, true, originalCreatePlugin::registerTypeWrappers);
        registry.register(new ItemStackAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new FluidStackAdapter());
        registry.register(new SizedIngredientAdapter());
        registry.register(new SizedFluidIngredientAdapter());
        registry.register(new LegacyTickDurationAdapter());
        return registry;
    }

    public static List<AdapterCatalogEntry> catalog(JSTypeAdapterRegistry registry) {
        return registry.view().stream().map(adapter -> new AdapterCatalogEntry(adapter.getTargetClass(),
                adapter.inputShapes(), adapter.getPrecedence(), adapter.syntaxDoc())).toList();
    }
}
