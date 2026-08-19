package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.platform.Platform;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Client-side wiring for the 1.12.2 pack sync: installs the CLIENT script
 * reload hook (whole-type reload, same path as the F3+T resource reload) and
 * deactivates remote packs on disconnect (cached files are kept and reused by
 * hash on the next join).
 *
 * <p>Registered from {@link NekoJSNetwork#init()} on the client side only.
 */
public final class PackSyncClientConnections {

    private PackSyncClientConnections() {}

    public static void install() {
        PackSyncClient.installClientReloadHook(PackSyncClientConnections::reloadClientScripts);
    }

    private static void reloadClientScripts() {
        if (!Platform.isClient() || NekoJSMod.RUNTIME_ROOT == null) return;
        if (NekoJSMod.RUNTIME_ROOT.scriptManagerOrNull(ScriptType.CLIENT) == null) return;
        NekoJS.LOGGER.debug("Reloading CLIENT scripts after server pack sync change");
        NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
    }

    @SubscribeEvent
    public static void onClientDisconnection(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        PackSyncClient.handleDisconnect();
    }
}
