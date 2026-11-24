package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// Extension point is a way of fetching information from registered plugins and/or other extension
/// points, and produces new products for later usage. Definition includes: its id, the plugin type
/// it collects, the environment it runs in, and the accumulator lifecycle that turns plugin
/// contributions into a product.
///
/// Points are registered by calling [NekoPluginExtensionRegistry#register] in a plugin that
/// implements [NekoPluginExtensionProvider]. Registration returns a [NekoPluginExtensionHandle],
/// which yields the product once bootstrap has completed.
///
/// The ordering of loading points are determined by [#dependsOn()] and [#optionalDependsOn()],
/// there's no guarantee that load order can be affected by other factors (e.g. registration order).
/// Cycles and dependencies on unregistered points will simply fail before any collection runs.
///
/// A point that needs the other's product can read it through [NekoPluginExtensionContext], which
/// fails if the other point is registered but not finished yet.
///
/// An accumulator (return value of [#initializer()]) that implements [Sealable] is sealed once
/// `finisher` returns.
///
/// @param id Extension point id, unique within one bootstrap. Built-in ids use `nekojs:*`; third parties should prefix theirs with their mod id.
/// @param pluginType [Class]-based plugin filter. Only plugins that are instances of this type will reach `collector`.
/// @param enabled Environment predicate. Returning `false` will skip the whole point: no stage runs and no product is published.
/// @param initializer Creates a mutable accumulator object for [#collector()]. It receives [NekoPluginExtensionContext] for reading result from previous points
/// @param collector Adds one plugin's contribution to the accumulator.
/// @param finisher Maps the accumulator to the final result, called after all plugins have been [collected](#collector).
/// @param dependsOn Required dependencies, as point instances or string ids.
/// @param optionalDependsOn Optional dependencies, as point instances or string ids. This will not cause failure when dependency is missing, but still affects load ordering
/// @param mergePolicy How duplicate contributions to a keyed accumulator are resolved.
public record NekoPluginExtensionPoint<P extends NekoJSPlugin, A, R>(
    String id,
    Class<P> pluginType,
    Predicate<NekoPluginExtensionContext> enabled,
    Function<NekoPluginExtensionContext, A> initializer,
    BiConsumer<P, A> collector,
    Function<A, R> finisher,
    List<Object> dependsOn,
    List<Object> optionalDependsOn,
    MergePolicy mergePolicy
) {
    public NekoPluginExtensionPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(pluginType, "pluginType required for an extension point");
        Objects.requireNonNull(enabled, "enabled predicate required for an extension point");
        Objects.requireNonNull(initializer, "initializer required for an extension point");
        Objects.requireNonNull(collector, "collector required for an extension point");
        Objects.requireNonNull(finisher, "finisher required for an extension point");
        Objects.requireNonNull(mergePolicy, "mergePolicy required for an extension point");
        dependsOn = List.copyOf(dependsOn);
        optionalDependsOn = List.copyOf(optionalDependsOn);
    }

    /// Normalize dependency element ids (instance or String).
    static String dependencyId(Object dependency) {
        if (dependency instanceof NekoPluginExtensionPoint<?, ?, ?> point) {
            return point.id();
        }
        return (String) dependency;
    }

    /// Starts a new extension point definition.
    public static <P extends NekoJSPlugin, A, R> Builder<P, A, R> builder(String id, Class<P> pluginType) {
        return new Builder<>(id, pluginType);
    }

    /// Builder for [NekoPluginExtensionPoint]; `merge` has no default and must be set.
    public static final class Builder<P extends NekoJSPlugin, A, R> {
        private final String id;
        private final Class<P> pluginType;
        private Predicate<NekoPluginExtensionContext> enabled = context -> true;
        private Function<NekoPluginExtensionContext, A> initializer;
        private BiConsumer<P, A> collector;
        private Function<A, R> finisher;
        private final List<Object> dependencies = new ArrayList<>();
        private final List<Object> optionalDependencies = new ArrayList<>();
        private MergePolicy mergePolicy;

        private Builder(String id, Class<P> pluginType) {
            this.id = id;
            this.pluginType = pluginType;
        }

        /// Merge policy (required): select one of append, firstWin, overrideWarn, or failFast.
        public Builder<P, A, R> merge(MergePolicy policy) {
            this.mergePolicy = policy;
            return this;
        }

        /// Environment predicate (defaults to collection in all environments).
        public Builder<P, A, R> enabledWhen(Predicate<NekoPluginExtensionContext> predicate) {
            this.enabled = predicate;
            return this;
        }

        /// Client-only collection (equivalent to `enabledWhen(NekoPluginExtensionContext::client)`).
        public Builder<P, A, R> clientOnly() {
            return enabledWhen(NekoPluginExtensionContext::client);
        }

        /// Accumulator factory (required).
        public Builder<P, A, R> initializer(Function<NekoPluginExtensionContext, A> initializer) {
            this.initializer = initializer;
            return this;
        }

        /// Collection callback (required).
        public Builder<P, A, R> collector(BiConsumer<P, A> collector) {
            this.collector = collector;
            return this;
        }

        /// Finishing function (required).
        public Builder<P, A, R> finish(Function<A, R> finisher) {
            this.finisher = finisher;
            return this;
        }

        /// Chronological dependencies: points that must finish first.
        public Builder<P, A, R> dependsOn(NekoPluginExtensionPoint<?, ?, ?>... points) {
            dependencies.addAll(List.of(points));
            return this;
        }

        /// Chronological dependencies by id, for targets that cannot be referenced as an instance.
        public Builder<P, A, R> dependsOnId(String... pointIds) {
            dependencies.addAll(List.of(pointIds));
            return this;
        }

        /// Optional dependencies: ordered before this point only when the target is registered, so
        /// a target that is absent in some environments is tolerated.
        public Builder<P, A, R> dependsOnOptional(NekoPluginExtensionPoint<?, ?, ?>... points) {
            optionalDependencies.addAll(List.of(points));
            return this;
        }

        /// Optional dependency by id, for cross-layer targets that cannot be referenced as an
        /// instance; see [#dependsOnOptional].
        public Builder<P, A, R> dependsOnOptionalId(String... pointIds) {
            optionalDependencies.addAll(List.of(pointIds));
            return this;
        }

        /// Build extension point; required-field validation is performed by the constructor.
        public NekoPluginExtensionPoint<P, A, R> build() {
            return new NekoPluginExtensionPoint<>(
                id, pluginType, enabled, initializer, collector, finisher,
                dependencies, optionalDependencies, mergePolicy
            );
        }
    }

}
