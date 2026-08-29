package com.tkisor.nekojs.core.module;

import graal.graalvm.polyglot.Value;

record ModuleState(String filename, Value value) {
    Object exports() {
        return value.getMember("exports");
    }

    void exports(Object exports) {
        value.putMember("exports", exports);
    }

    void loaded(boolean loaded) {
        value.putMember("loaded", loaded);
    }
}
