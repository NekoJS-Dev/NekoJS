package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A complete self-contained definition of an extension point: id + target plugin type + environment predicate + accumulator lifecycle
 * + dependency declaration + merge strategy (ADR-0001 V2 model).
 *
 * <p>Extension points are registered by {@link NekoPluginExtensionProvider} during the bootstrap registration phase via
 * {@link NekoPluginExtensionRegistry#register} (the 14 inherent hooks of {@code NekoJSPlugin}
 * are also registered as built-in extension points in this way, with the id {@code nekojs:*}). Registration returns
 * {@link NekoPluginExtensionHandle}, which can be used to retrieve artifacts after bootstrap completes.
 *
 * <p><b>Dependencies (ADR-0002 Dual-track System):</b> After freeze, bootstrap collects points in Kahn topological order (same-layer by registration order, built-ins guaranteed as graph roots).
 * 1) Chronological dependency: declared via builder {@code dependsOn(point)}/{@code dependsOnId(id)}—ensures target completes first; does not read artifacts; fail-fast on cycles (prints full cycle path), early-bang on unregistered ids.
 * 2) Data dependency: reading precedent artifacts in initializer/collector via {@link NekoPluginExtensionContext#result}, no declaration needed; out-of-order access (target registered but not finished) throws {@link IllegalStateException} with "declare dependsOn" instruction.
 * 3) Optional chronological dependency: declared via {@code dependsOnOptional(point)}/{@code dependsOnOptionalId(id)}; adds topological edge only if target <b>is registered</b> (bootstrap without target creates no edge, no early-bang), used for cross-layer data dependencies where target's existence is environment-dependent; chronological guarantee same as hard dependency, out-of-order access still throws per 2.
 *
 * <p><b>Merge Policy (ADR-0001 Four Standard Options):</b> Builder {@code merge(...)} is mandatory, explicitly select from
 * {@link MergePolicy} options: {@code append / firstWin / overrideWarn / failFast};
 * for keyed accumulators, {@link MergePolicy#resolveDuplicate} is called at conflict points.
 *
 * <p><b>Reload re-entrancy:</b> Bootstrap re-invokes {@code initializer} to create a new accumulator each round; the extension point holds no cross-round state. Artifacts are derived from accumulator snapshots via {@code finisher}, decoupling from the accumulator. The engine seals accumulators implementing {@link Sealable} after finisher execution (collection disallowed after finish).
 *
 * @param id          Extension point id, globally unique within one bootstrap (conflicts with built-in {@code nekojs:*} throw {@link IllegalArgumentException}); third-parties should use {@code modid:name} namespace prefix. Must not be null or blank
 * @param pluginType  Target plugin type filter: only plugins where {@code pluginType.isInstance(plugin)} is true are collected; others are skipped without collector invocation.
 * @param enabled     Environment predicate: tested once per extension point via {@link NekoPluginExtensionContext} before collection; if {@code false}, the entire extension point is skipped in the current environment (e.g., dedicated server)—initializer/collector/finisher are not executed, and no artifacts are published.
 * @param initializer Accumulator factory: creates a new mutable accumulator {@code A} per bootstrap round; receives current {@link NekoPluginExtensionContext}, allowing customization based on environment or precedent artifact results. Must not return a shared mutable singleton across rounds.
 * @param collector   Collection callback: invoked once per plugin passing both environment and type filters to collect into the accumulator.
 * @param finisher    Finishing function: maps accumulator to immutable artifact {@code R}. Invoked once after all plugins are collected for this extension point; artifacts are published immediately. Perform side-effecting completion (locking/static injection) here, not in the bootstrap flow.
 * @param dependsOn   Dependency declarations: elements are extension point instances or String ids
 * @param <P>         Target plugin type (subtype of {@link NekoJSPlugin})
 * @param <A>         Accumulator type (mutable state during collection, created per bootstrap round)
 * @param <R>         Artifact type (immutable result produced by finisher)
 */
public final class NekoPluginExtensionPoint<P extends NekoJSPlugin, A, R> {
    private final String id;
    private final Class<P> pluginType;
    private final Predicate<NekoPluginExtensionContext> enabled;
    private final Function<NekoPluginExtensionContext, A> initializer;
    private final BiConsumer<P, A> collector;
    private final Function<A, R> finisher;
    private final List<Object> dependsOn;
    private final List<Object> optionalDependsOn;
    private final MergePolicy mergePolicy;

    private NekoPluginExtensionPoint(
            String id,
            Class<P> pluginType,
            Predicate<NekoPluginExtensionContext> enabled,
            Function<NekoPluginExtensionContext, A> initializer,
            BiConsumer<P, A> collector,
            Function<A, R> finisher,
            List<Object> dependsOn,
            List<Object> optionalDependsOn,
            MergePolicy mergePolicy) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(pluginType, "pluginType");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(collector, "collector");
        Objects.requireNonNull(finisher, "finisher");
        Objects.requireNonNull(mergePolicy, "mergePolicy");
        this.id = id;
        this.pluginType = pluginType;
        this.enabled = enabled;
        this.initializer = initializer;
        this.collector = collector;
        this.finisher = finisher;
        this.dependsOn = List.copyOf(dependsOn);
        this.optionalDependsOn = List.copyOf(optionalDependsOn);
        this.mergePolicy = mergePolicy;
    }

    /** Extension point id (globally unique within the same bootstrap round). */
    public String id() {
        return id;
    }

    /** Target plugin type filter. */
    public Class<P> pluginType() {
        return pluginType;
    }

    /** Environment predicate. */
    public Predicate<NekoPluginExtensionContext> enabled() {
        return enabled;
    }

    /** Accumulator factory. */
    public Function<NekoPluginExtensionContext, A> initializer() {
        return initializer;
    }

    /** Collection callback. */
    public BiConsumer<P, A> collector() {
        return collector;
    }

    /** Finishing function. */
    public Function<A, R> finisher() {
        return finisher;
    }

    /** Dependency declarations (elements are extension point instances or String ids, immutable). */
    public List<Object> dependsOn() {
        return dependsOn;
    }

    /**
     * Optional chronological dependency declarations (elements are extension point instances or String ids, immutable): participates in topological sort only if the target is registered;
     * bootstrap without the target creates no edge (and no early-bang).
     */
    public List<Object> optionalDependsOn() {
        return optionalDependsOn;
    }

    /** Merge policy. */
    public MergePolicy mergePolicy() {
        return mergePolicy;
    }

    /** Normalize dependency element ids (instance or String). */
    static String dependencyId(Object dependency) {
        if (dependency instanceof NekoPluginExtensionPoint<?, ?, ?> point) {
            return point.id();
        }
        return (String) dependency;
    }

    // ---- V2 builder (ADR-0001: Sole recommended entry, merge required) ----

    /** Create extension point builder. */
    public static <P extends NekoJSPlugin, A, R> Builder<P, A, R> builder(String id, Class<P> pluginType) {
        return new Builder<>(id, pluginType);
    }

    /** V2 extension point builder: {@code merge} is required (no default provided), dependencies can be declared via point instance or id. */
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

        /** Merge policy (required): select one of append, firstWin, overrideWarn, or failFast. */
        public Builder<P, A, R> merge(MergePolicy policy) {
            this.mergePolicy = Objects.requireNonNull(policy, "mergePolicy");
            return this;
        }

        /** Environment predicate (defaults to collection in all environments). */
        public Builder<P, A, R> enabledWhen(Predicate<NekoPluginExtensionContext> predicate) {
            this.enabled = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        /** Client-only collection (equivalent to {@code enabledWhen(NekoPluginExtensionContext::client)}). */
        public Builder<P, A, R> clientOnly() {
            return enabledWhen(NekoPluginExtensionContext::client);
        }

        /** Accumulator factory (required). */
        public Builder<P, A, R> initializer(Function<NekoPluginExtensionContext, A> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer");
            return this;
        }

        /** Collection callback (required). */
        public Builder<P, A, R> collector(BiConsumer<P, A> collector) {
            this.collector = Objects.requireNonNull(collector, "collector");
            return this;
        }

        /** Finishing function (required). */
        public Builder<P, A, R> finish(Function<A, R> finisher) {
            this.finisher = Objects.requireNonNull(finisher, "finisher");
            return this;
        }

        /** Chronological dependencies: required extension point instances that must complete first (ADR-0002-1). */
        @SafeVarargs
        public final Builder<P, A, R> dependsOn(NekoPluginExtensionPoint<?, ?, ?>... points) {
            dependencies.addAll(List.of(points));
            return this;
        }

        /** Chronological dependencies: required extension point ids (for cases where static reference to the target instance is not possible). */
        public Builder<P, A, R> dependsOnId(String... pointIds) {
            dependencies.addAll(List.of(pointIds));
            return this;
        }

        /**
         * Optional chronological dependencies: adds topological edge only if the target is registered (bootstrap without the target creates no edge and no early-bang).
         * Used for cross-layer data dependencies where "reading target artifacts, but the target does not exist in all environments" is necessary.
         */
        @SafeVarargs
        public final Builder<P, A, R> dependsOnOptional(NekoPluginExtensionPoint<?, ?, ?>... points) {
            optionalDependencies.addAll(List.of(points));
            return this;
        }

        /** Optional chronological dependency id form (the only feasible form when referencing targets across layers; see {@link #dependsOnOptional}). */
        public Builder<P, A, R> dependsOnOptionalId(String... pointIds) {
            optionalDependencies.addAll(List.of(pointIds));
            return this;
        }

        /** Build extension point; throws {@link IllegalStateException} if merge, initializer, collector, or finisher is missing. */
        public NekoPluginExtensionPoint<P, A, R> build() {
            if (mergePolicy == null) {
                throw new IllegalStateException("Plugin extension point '" + id
                        + "' requires an explicit merge policy (append/firstWin/overrideWarn/failFast)");
            }
            if (initializer == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires an initializer");
            }
            if (collector == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires a collector");
            }
            if (finisher == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires a finisher");
            }
            return new NekoPluginExtensionPoint<>(
                    id, pluginType, enabled, initializer, collector, finisher,
                    dependencies, optionalDependencies, mergePolicy);
        }
    }

}
