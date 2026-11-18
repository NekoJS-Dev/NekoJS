package com.tkisor.nekojs.core.config;

import java.nio.file.Path;

/** Raised when engine settings cannot be loaded or resolved. */
public final class EngineConfigException extends IllegalArgumentException {
    public EngineConfigException(Path path, String detail) {
        this(path, detail, null);
    }

    public EngineConfigException(Path path, String detail, Throwable cause) {
        super("[NEKO-1030] Invalid engine config at " + path + ": " + detail, cause);
    }
}
