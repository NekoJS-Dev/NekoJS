package com.example.addon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;

/**
 * Fixture plugin requiring a mod that is never present in the test worlds:
 * discovery must filter it out through the {@code requiredMods} AND rule.
 */
@RegisterNekoJSPlugin(priority = 800, requiredMods = {"exampleaddon-never-installed"})
public final class ExampleAddonMissingModPlugin implements NekoJSPlugin {
}
