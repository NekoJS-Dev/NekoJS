package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public record NekoPluginExtensionPoint<P extends NekoJSPlugin>(
        String id,
        Class<P> pluginType,
        Predicate<NekoPluginExtensionContext> enabled,
        BiConsumer<P, NekoPluginExtensionContext> collector
) {
    public NekoPluginExtensionPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(pluginType, "pluginType");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(collector, "collector");
    }

    public static <P extends NekoJSPlugin> NekoPluginExtensionPoint<P> of(
            String id,
            Class<P> pluginType,
            BiConsumer<P, NekoPluginExtensionContext> collector
    ) {
        return new NekoPluginExtensionPoint<>(id, pluginType, context -> true, collector);
    }

    public static <P extends NekoJSPlugin> NekoPluginExtensionPoint<P> clientOnly(
            String id,
            Class<P> pluginType,
            BiConsumer<P, NekoPluginExtensionContext> collector
    ) {
        return new NekoPluginExtensionPoint<>(id, pluginType, NekoPluginExtensionContext::client, collector);
    }

    public void collect(NekoJSPlugin plugin, NekoPluginExtensionContext context) {
        if (enabled.test(context) && pluginType.isInstance(plugin)) {
            collector.accept(pluginType.cast(plugin), context);
        }
    }
}
