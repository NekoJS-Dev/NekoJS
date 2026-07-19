package com.tkisor.nekojs;

import com.tkisor.nekojs.core.NekoJSMemberRemapper;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.mod.api.MemberRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NekoJS {
    public static final String MODID = "nekojs";
    public static final Logger LOGGER = LogManager.getLogger("NekoJS");

    public final ScriptPropertyRegistry scriptProperties = new ScriptPropertyRegistry.Impl();
    public final ScriptTypedValue<ScriptManager> scriptManagers = ScriptTypedValue.of();
    public final ScriptEventBridge scriptEventBridge;

    static {
        MemberRemapper.CHAIN.addRemapper(new NekoJSMemberRemapper());
    }

    public NekoJS(ScriptEventBridge scriptEventBridge) {
        this.scriptEventBridge = scriptEventBridge;
    }
}
