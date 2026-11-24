package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.plugin.NekoExtensionPoint;
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

    public static final NekoExtensionPoint<ScriptPreflightCatalog> POINT =
            NekoExtensionPoint.builder(ID)
                    .initializer(context -> new ScriptPreflightCatalog.Collector())
                    .collector(NekoJSPlugin::registerScriptPreflights)
                    .finisher(ScriptPreflightCatalog.Collector::freeze)
                    .build();
}
