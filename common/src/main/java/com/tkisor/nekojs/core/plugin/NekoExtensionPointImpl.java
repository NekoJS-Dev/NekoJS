package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.plugin.NekoExtensionPoint;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// Engine-side extension point, produced by [NekoExtensionPoint#builder].
///
/// The public [NekoExtensionPoint] contract stops at identity and dependency views; the lifecycle
/// callbacks are reachable only through this type, which engine code casts to after registration.
///
/// @param id Extension point id, unique within one bootstrap. Built-in ids use `nekojs:*`; third
///           parties prefix theirs with their mod id.
/// @param pluginType Plugin filter: only plugins that are instances of this type reach `collector`.
///                   `null` at construction means every [NekoJSPlugin].
/// @param enabled Environment predicate. Returning `false` skips the whole point: no stage runs and
///                no product is published. `null` at construction means every environment.
/// @param initializer Creates the mutable accumulator handed to `collector`. Receives
///                    [NekoPluginExtensionContext] for reading results of earlier points.
/// @param collector Adds one plugin's contribution to the accumulator.
/// @param finisher Maps the accumulator to the product, called after every plugin was collected.
/// @param dependencies Required dependencies, as point ids.
/// @param optionalDependencies Optional dependencies, as point ids: a target that is absent from
///                             some environment is tolerated, ordering is still applied when it is
///                             registered.
public record NekoExtensionPointImpl<P extends NekoJSPlugin, A, R>(
    String id,
    Class<P> pluginType,
    Predicate<NekoPluginExtensionContext> enabled,
    Function<NekoPluginExtensionContext, A> initializer,
    BiConsumer<P, A> collector,
    Function<A, R> finisher,
    Set<String> dependencies,
    Set<String> optionalDependencies
) implements NekoExtensionPoint<R> {

    private static final Predicate<NekoPluginExtensionContext> ALWAYS = context -> true;

    /// `null` `pluginType` and `enabled` are meaningful at the builder, so they are normalized here
    /// instead of at every use site.
    @SuppressWarnings("unchecked")
    public NekoExtensionPointImpl {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(initializer, "initializer == null");
        Objects.requireNonNull(collector, "collector == null");
        Objects.requireNonNull(finisher, "finisher == null");
        pluginType = pluginType != null ? pluginType : (Class<P>) NekoJSPlugin.class;
        enabled = enabled != null ? enabled : ALWAYS;
        dependencies = Set.copyOf(dependencies);
        optionalDependencies = Set.copyOf(optionalDependencies);
    }

    /// All null check are deferred to record ctor, DO NOT add redundant null check here
    public static final class BuilderImpl<P extends NekoJSPlugin, A, R> implements Builder<P, A, R> {
        private final String id;
        private Class<P> pluginType;
        private Predicate<NekoPluginExtensionContext> enabled;
        private Function<NekoPluginExtensionContext, A> initializer;
        private BiConsumer<P, A> collector;
        private Function<A, R> finisher;
        private final Set<String> dependencies = new HashSet<>();
        private final Set<String> optionalDependencies = new HashSet<>();

        public BuilderImpl(String id) {
            this.id = id;
        }

        @SuppressWarnings("unchecked")
        private <P_ extends NekoJSPlugin, A_, R_> BuilderImpl<P_, A_, R_> castThis() {
            return (BuilderImpl<P_, A_, R_>) this;
        }

        @Override
        public Builder<P, A, R> enabledWhen(Predicate<NekoPluginExtensionContext> predicate) {
            enabled = predicate;
            return this;
        }

        @Override
        public Builder<P, A, R> clientOnly() {
            return enabledWhen(NekoPluginExtensionContext::client);
        }

        @Override
        public <P_ extends NekoJSPlugin> Builder<P_, A, R> requirePluginType(Class<P_> pluginType) {
            var builder = this.<P_, A, R>castThis();
            builder.pluginType = pluginType;
            return builder;
        }

        @Override
        public <A_> Builder<P, A_, R> initializer(Function<NekoPluginExtensionContext, A_> initializer) {
            var builder = this.<P, A_, R>castThis();
            builder.initializer = initializer;
            return builder;
        }

        @Override
        public Builder<P, A, A> collector(BiConsumer<P, A> collector) {
            var builder = this.<P, A, A>castThis();
            builder.collector = collector;
            builder.finisher = Function.identity();
            return builder;
        }

        @Override
        public <R_> Builder<P, A, R_> finisher(Function<A, R_> finisher) {
            var builder = this.<P, A, R_>castThis();
            builder.finisher = finisher;
            return builder;
        }

        @Override
        public Builder<P, A, R> dependsOn(NekoExtensionPoint<?>... points) {
            return dependsOn(Arrays.stream(points).map(NekoExtensionPoint::id).toArray(String[]::new));
        }

        @Override
        public Builder<P, A, R> dependsOn(String... pointIds) {
            this.dependencies.addAll(Arrays.asList(pointIds));
            return this;
        }

        @Override
        public Builder<P, A, R> dependsOnOptional(NekoExtensionPoint<?>... points) {
            return dependsOnOptional(Arrays.stream(points).map(NekoExtensionPoint::id).toArray(String[]::new));
        }

        @Override
        public Builder<P, A, R> dependsOnOptional(String... pointIds) {
            this.optionalDependencies.addAll(Arrays.asList(pointIds));
            return this;
        }

        @Override
        public NekoExtensionPoint<R> build() {
            return new NekoExtensionPointImpl<>(id, pluginType, enabled, initializer, collector, finisher, dependencies, optionalDependencies);
        }
    }
}
