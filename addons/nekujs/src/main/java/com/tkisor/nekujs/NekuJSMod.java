package com.tkisor.nekujs;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;

/** Loader entry for the separately packaged NekoJS compatibility addon. */
@Mod(NekuJSMod.ID)
public final class NekuJSMod {
    public static final String ID = "nekujs";

    public NekuJSMod() {
        LogUtils.getLogger().info("NekuJS initialized with bounded native KubeJS/Rhino compatibility; unsupported APIs fail explicitly");
    }
}
