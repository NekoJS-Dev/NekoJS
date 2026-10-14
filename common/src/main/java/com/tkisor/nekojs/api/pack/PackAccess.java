package com.tkisor.nekojs.api.pack;

import graal.graalvm.polyglot.Value;

/** Capabilities exchange JSON data within one type and source session. Handles expire with either activation. */
public interface PackAccess {
    String getSelector();
    long getActivation();
    void provide(String name, Value callback);
    Value require(String provider, String name);
}
