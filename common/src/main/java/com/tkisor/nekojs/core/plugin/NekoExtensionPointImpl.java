package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.plugin.NekoExtensionPoint;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// @param pluginType `null` means base [NekoJSPlugin]
/// @param enabled `null` means always true
public record NekoExtensionPointImpl<P extends NekoJSPlugin, A, R>(
    String id,
    @Nullable Class<P> pluginType,
    @Nullable Predicate<NekoPluginExtensionContext> enabled,
    Function<NekoPluginExtensionContext, A> initializer,
    BiConsumer<P, A> collector,
    Function<A, R> finisher,
    Set<String> dependencies,
    Set<String> optionalDependencies
) implements NekoExtensionPoint<R> {
    public NekoExtensionPointImpl {
        Objects.requireNonNull(id, "id == null");
        Objects.requireNonNull(initializer, "initializer == null");
        Objects.requireNonNull(collector, "collector == null");
        Objects.requireNonNull(finisher, "finisher == null");
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
        public Builder<P, A, R> collector(BiConsumer<P, A> collector) {
            this.collector = collector;
            return this;
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
