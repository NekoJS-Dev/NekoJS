package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

/**
 * Standalone plugin exposing the {@code DynamicRegistry} binding to SERVER
 * scripts (NeoForge 26.1/26.2, via neoforge-26-shared).
 *
 * <p>The binding object is a stateless view over {@link DynamicRegistries};
 * per-reload behavior (stale-retain claims) hangs off the binding's
 * {@code close(ScriptType)} hook, see {@link DynamicRegistryBinding}.
 */
@RegisterNekoJSPlugin
public final class DynamicRegistryPlugin implements NekoJSPlugin {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.SERVER) {
            registry.register(new DynamicRegistryBinding());
        }
    }
}
