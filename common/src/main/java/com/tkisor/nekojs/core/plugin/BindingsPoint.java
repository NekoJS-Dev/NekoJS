package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.script.ScriptTypedValue;

import java.util.Map;

/**
 * The built-in {@code nekojs:bindings} extension point; a self-contained Point file (ADR-0001).
 *
 * <p><b>Closure special case</b>: the collector filters ScriptType by the client environment (a
 * dedicated server collects no CLIENT bindings), and the predicate captures the client value of
 * each bootstrap round. The {@link #point(boolean)} static factory builds it at registration
 * time.
 *
 * <p>The accumulator is a {@link BindingRegistry} created lazily per {@link ScriptType}; the
 * finisher freezes it into a {@code ScriptType → (name → Binding)} snapshot.
 */
public final class BindingsPoint {

    /** The extension point id; part of the observable contract. */
    public static final String ID = "nekojs:bindings";

    private BindingsPoint() {
    }

    /** Explicit declaration form, collected the same way as overriding the matching NekoJSPlugin
     * hook: the plugin is collected by the {@code nekojs:bindings} extension point. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers global bindings: global names a script can reference directly, such as
         * {@code Item}/{@code Ingredient}. A binding can be a Java class, an instance, or a value
         * type declared explicitly with {@code Binding.of(...)} for preflight checks.
         */
        default void registerBinding(BindingRegistry registry) {
        }
    }

    /** Keeps the historical map product while retaining module exports in the same frozen collection. */
    static final class Snapshot extends java.util.AbstractMap<ScriptType, Map<String, Binding>> {
        private final Map<ScriptType, Map<String, Binding>> globals;
        private final java.util.List<com.tkisor.nekojs.api.data.ModuleBinding> modules;

        Snapshot(Map<ScriptType, Map<String, Binding>> globals,
                 java.util.List<com.tkisor.nekojs.api.data.ModuleBinding> modules) {
            this.globals = Map.copyOf(globals);
            this.modules = java.util.List.copyOf(modules);
        }

        java.util.List<com.tkisor.nekojs.api.data.ModuleBinding> modules() {
            return modules;
        }

        @Override
        public java.util.Set<Entry<ScriptType, Map<String, Binding>>> entrySet() {
            return globals.entrySet();
        }
    }

    /** The extension point definition, built when the {@link NekoBuiltinPointsPlugin} manifest
     * registers it. */
    public static NekoPluginExtensionPoint<NekoJSPlugin, ScriptTypedValue<BindingRegistry>, Map<ScriptType, Map<String, Binding>>> point(
            boolean client) {
        return point(client, plugin -> null);
    }

    static NekoPluginExtensionPoint<NekoJSPlugin, ScriptTypedValue<BindingRegistry>, Map<ScriptType, Map<String, Binding>>> point(
            boolean client, java.util.function.Function<NekoJSPlugin, String> owners) {
        var predicate = NekoPluginBootstrap.bindingPredicate(client);
        return NekoPluginExtensionPoint.<NekoJSPlugin, ScriptTypedValue<BindingRegistry>, Map<ScriptType, Map<String, Binding>>>builder(ID, NekoJSPlugin.class)
                .merge(MergePolicy.append())
                .initializer(context -> ScriptTypedValue.of(BindingRegistry.BindingRegistryImpl::new))
                .collector((plugin, registries) -> predicate.streamMatched()
                        .map(registries::at)
                        .map(registry -> ((BindingRegistry.BindingRegistryImpl) registry).scopedTo(owners.apply(plugin)))
                        .forEach(plugin::registerBinding))
                .finish(NekoPluginBootstrap::freezeBindings)
                .build();
    }
}
