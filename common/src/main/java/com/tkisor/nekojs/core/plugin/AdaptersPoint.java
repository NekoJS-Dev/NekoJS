package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

import java.util.List;

/**
 * The built-in {@code nekojs:adapters} extension point; a self-contained Point file (ADR-0001).
 * The merge policy is {@link MergePolicy#append}, which collects a list of adapters. The
 * accumulator is {@link JSTypeAdapterRegistry.Impl} (registry form).
 */
public final class AdaptersPoint {

    /** The extension point id; part of the observable contract. */
    public static final String ID = "nekojs:adapters";

    private AdaptersPoint() {
    }

    /** Explicit declaration form, collected the same way as overriding the matching NekoJSPlugin
     * hook: the plugin is collected by the {@code nekojs:adapters} extension point. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers JS↔Java type adapters, so parameters such as {@code string → ItemStack} convert
         * automatically. The adapters also drive generation of the probe input aliases
         * ({@code $ItemStack_}).
         */
        default void registerAdapters(JSTypeAdapterRegistry registry) {
        }
    }

    /** The extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, JSTypeAdapterRegistry, List<Object>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, JSTypeAdapterRegistry, List<Object>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new JSTypeAdapterRegistry.Impl())
                    .collector(NekoJSPlugin::registerAdapters)
                    .finish(registry -> List.copyOf(registry.view()))
                    .build();
}
