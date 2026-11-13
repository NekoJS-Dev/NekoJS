package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;

/**
 * Built-in extension point {@code nekojs:probe_backends}.
 *
 * <p>The accumulator is the {@link ProbeBackendRegistry} itself. The finisher assembles the
 * result: it calls {@code lock()}, which fails fast on conflicts and seals the registry, then
 * installs the global singleton. A second bootstrap during a full reload replaces that
 * singleton instead of crashing. The merge policy is {@link MergePolicy#failFast}: two entries
 * with the same {@code (languageId, name)} conflict at lock time, which matches the registry's
 * own rule.
 */
public final class ProbeBackendsPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:probe_backends";

    private ProbeBackendsPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:probe_backends} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers a probe backend, keyed by {@code (languageId, name)}.
         *
         * <p>The built-in backends (TypeScript {@code .d.ts} and Python {@code .pyi}) are registered by
         * {@code NekoProbeBuiltinPlugin} through this method, the same path a third party uses. A third
         * party can register a backend for another language, or provide an alternative backend for an
         * existing language under a different {@code name}. Two entries with the same language and name
         * conflict when bootstrap ends and the registry locks.
         */
        void registerProbeBackends(ProbeBackendRegistry registry);
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, ProbeBackendRegistry, ProbeBackendRegistry> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, ProbeBackendRegistry, ProbeBackendRegistry>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new ProbeBackendRegistry())
                    .collector(NekoJSPlugin::registerProbeBackends)
                    .finish(registry -> {
                        registry.lock();
                        ProbeBackendRegistry.setInstance(registry);
                        return registry;
                    })
                    .build();
}
