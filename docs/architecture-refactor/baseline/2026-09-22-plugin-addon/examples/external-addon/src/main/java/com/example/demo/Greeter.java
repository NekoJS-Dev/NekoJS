package com.example.demo;

import com.tkisor.nekojs.api.NekoJSPlugin;

/** Contributor face collected by the custom extension point {@code demomod:greetings}. */
public interface Greeter extends NekoJSPlugin {

    /** Greeting line the custom point collects from every greeter plugin. */
    String greeting();
}
