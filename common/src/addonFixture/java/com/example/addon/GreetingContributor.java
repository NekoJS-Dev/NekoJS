package com.example.addon;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * Contributor face of the fixture addon's custom extension point
 * {@code exampleaddon:greetings}: every discovered plugin implementing this
 * interface is collected by the point, in plugin priority order.
 */
public interface GreetingContributor extends NekoJSPlugin {

    /** Greeting line collected by the custom extension point. */
    String greeting();
}
