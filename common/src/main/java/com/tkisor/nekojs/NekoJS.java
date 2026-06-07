package com.tkisor.nekojs;

import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NekoJS {
    public static final String MODID = "nekojs";
    /** Keep in sync with {@code gradle.properties mod_version}. */
    public static final String VERSION = "1.0.7";
    public static final Logger LOGGER = LogManager.getLogger("NekoJS");

    public static NekoJS COMMON;

    public final ScriptPropertyRegistry scriptProperties = new ScriptPropertyRegistry.Impl();
    public final ScriptTypedValue<ScriptManager> scriptManagers = ScriptTypedValue.of();
    public final ScriptEventBridge scriptEventBridge;

    public NekoJS(ScriptEventBridge scriptEventBridge) {
        this.scriptEventBridge = scriptEventBridge;
    }
}
