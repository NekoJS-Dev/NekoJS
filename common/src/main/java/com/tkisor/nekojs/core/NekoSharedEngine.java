package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Engine;

public final class NekoSharedEngine {
    private static final Engine SHARED_ENGINE = Engine.newBuilder("js")
            .allowExperimentalOptions(true)
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    private NekoSharedEngine() {}

    public static Engine get() {
        return SHARED_ENGINE;
    }
}
