//? if neoforge {
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client connection wiring for pack distribution (shared by NeoForge 26.x and 1.21.1):
 *
 * <ul>
 *   <li>Installs the CLIENT script reload hook: after activating or unloading the SERVER_CACHE
 *       packs, the {@link PackSyncClient} pipeline reloads the whole type, on the same path as the
 *       CLIENT reload of F3+T.</li>
 *   <li>On disconnect or leaving the world (including a disconnect by the trust gate), unloads the
 *       remote pack cache set and reloads client scripts (the cache files stay and are reused by
 *       hash on the next connection).</li>
 * </ul>
 *
 * <p>Only the client registers this: each platform's {@code NekoJSNetwork} calls {@link #install}
 * in the {@code Dist.CLIENT} branch. The {@code ClientPlayerNetworkEvent} this class references is
 * a client-only event class, so a dedicated server never loads this class.
 *
 * <p>The loader entry ({@code NekoJSMod}) injects the lifecycle handle with {@link #bind} after the
 * root is assembled. The hooks fire on pack sync changes, which is later than assembly, so the
 * null check on the root keeps the previous behavior: a hook that fires before assembly is skipped.
 */
public final class PackSyncClientConnections {

    /**
     * Narrow lifecycle handle injected by the loader entry after assembly. A hook that fires while it is
     * null is skipped, as before.
     */
    private static volatile NekoRuntimeRoot runtimeRoot;

    private PackSyncClientConnections() {}

    /** Called by {@code NekoJSMod} after the root is assembled. */
    public static void bind(NekoRuntimeRoot root) {
        runtimeRoot = root;
    }

    static NekoRuntimeRoot runtimeRootOrNull() {
        return runtimeRoot;
    }

    public static void install() {
        PackSyncClient.installClientReloadHook(PackSyncClientConnections::reloadClientScripts);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PackSyncClientConnections::onLoggingOut);
    }

    private static boolean reloadClientScripts() {
        NekoRuntimeRoot root = runtimeRoot;
        if (!Platform.isClient() || root == null) {
            NekoJS.LOGGER.error("Cannot reload CLIENT scripts after server pack sync: client runtime is unavailable");
            return false;
        }
        var manager = root.scriptManagerOrNull(ScriptType.CLIENT);
        if (manager == null) {
            NekoJS.LOGGER.error("Cannot reload CLIENT scripts after server pack sync: CLIENT manager is unavailable");
            return false;
        }
        NekoJS.LOGGER.debug("Reloading CLIENT scripts after server pack sync change");
        try {
            return root.reload(ScriptType.CLIENT).success();
        } catch (Throwable failure) {
            NekoJS.LOGGER.error("CLIENT script reload after server pack sync failed", failure);
            return false;
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PackSyncClient.handleDisconnect(runtimeRoot);
    }
}
//?}
