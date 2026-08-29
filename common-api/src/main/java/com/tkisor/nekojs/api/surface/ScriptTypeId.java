package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.ScriptType;

public enum ScriptTypeId {
    STARTUP, SERVER, CLIENT, TEST;

    public static ScriptTypeId fromScriptType(ScriptType type) {
        return valueOf(type.name());
    }
}
