package com.example.addon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;

/**
 * Fixture plugin annotated {@code clientOnly = true}: on a dedicated-server
 * process (or the headless test platform) discovery must skip it silently.
 */
@RegisterNekoJSPlugin(priority = 900, clientOnly = true)
public final class ExampleAddonClientOnlyPlugin implements NekoJSPlugin {
}
