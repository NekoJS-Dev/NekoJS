package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.plugin.NekoExtensionPointImpl;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionContext;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionHandle;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.core.plugin.Sealable;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// Extension point is a way of fetching information from registered plugins and/or other extension
/// points, and produces new products for later usage.
///
/// A plugin defines points by calling [NekoPluginExtensionRegistry#register] from
/// [NekoPluginExtensionProvider#registerPluginExtensionPoints]. Registration returns a
/// [NekoPluginExtensionHandle], which yields the product once bootstrap has completed.
///
/// Collection order is determined by [#dependencies()] and [#optionalDependencies()]; no other
/// factor, registration order included, can change it. Cycles, and dependencies on missing points,
/// fail before any collection runs.
///
/// A point that needs another point's product can read it through [NekoPluginExtensionContext].
///
/// An accumulator (result of `initializer`) that implements [Sealable] is sealed once the finisher
/// returns. See [Builder] for more detail on `initializer` and `finisher`.
///
/// @param <R> Type of product of this extension point
public interface NekoExtensionPoint<R> {
    static Builder<NekoJSPlugin, Void, Void> builder(String id) {
        return new NekoExtensionPointImpl.BuilderImpl<>(id);
    }

    static <P extends NekoJSPlugin> Builder<P, Void, Void> builder(String id, Class<P> pluginType) {
        return builder(id).requirePluginType(pluginType);
    }

    String id();

    /// Immutable view of [#id()]s describing required dependencies for this extension point
    Set<String> dependencies();

    /// Immutable view of [#id()]s describing optional dependencies for this extension point
    Set<String> optionalDependencies();

    interface Builder<P extends NekoJSPlugin, A, R> {
        Builder<P, A, R> enabledWhen(Predicate<NekoPluginExtensionContext> predicate);

        Builder<P, A, R> clientOnly();

        <P_ extends NekoJSPlugin> Builder<P_, A, R> requirePluginType(Class<P_> pluginType);

        <A_> Builder<P, A_, R> initializer(Function<NekoPluginExtensionContext, A_> initializer);

        Builder<P, A, R> collector(BiConsumer<P, A> collector);

        <R_> Builder<P, A, R_> finisher(Function<A, R_> finisher);

        Builder<P, A, R> dependsOn(NekoExtensionPoint<?>... points);

        Builder<P, A, R> dependsOn(String... pointIds);

        Builder<P, A, R> dependsOnOptional(NekoExtensionPoint<?>... points);

        Builder<P, A, R> dependsOnOptional(String... pointIds);

        NekoExtensionPoint<R> build();
    }
}
