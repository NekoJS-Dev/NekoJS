package com.example.addon;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;

/**
 * Second fixture plugin in the same jar with a lower priority: proves the
 * custom extension point collects every plugin (not only its definer) and that
 * collection order follows the manager's priority ordering across discovered
 * plugins.
 */
@RegisterNekoJSPlugin(priority = 400)
public final class ExampleAddonSecondaryPlugin implements GreetingContributor {

    @Override
    public String greeting() {
        return "secondary";
    }
}
