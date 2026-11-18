package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.preflight.ScriptPreflightRegistry;
import com.tkisor.nekojs.core.preflight.ScriptPreflightCatalog;

/** Collects and freezes the bootstrap's preflight provider catalog without selecting providers. */
public final class ScriptPreflightsPoint {
    public static final String ID = "nekojs:script_preflights";

    private ScriptPreflightsPoint() {}

    public interface Contributor extends NekoJSPlugin {
        @Override
        void registerScriptPreflights(ScriptPreflightRegistry registry);
    }

    public static final NekoPluginExtensionPoint<NekoJSPlugin, ScriptPreflightCatalog.Collector, ScriptPreflightCatalog> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, ScriptPreflightCatalog.Collector, ScriptPreflightCatalog>builder(
                            ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new ScriptPreflightCatalog.Collector())
                    .collector(NekoJSPlugin::registerScriptPreflights)
                    .finish(ScriptPreflightCatalog.Collector::freeze)
                    .build();
}
